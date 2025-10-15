/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import groovy.transform.NamedVariant;
import net.minecraftforge.accesstransformers.gradle.ArtifactAccessTransformer;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.Objects;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    private @UnknownNullability ExternalModuleDependency delegate;
    private @UnknownNullability TaskProvider<SyncMavenizer> mavenizer;

    private final Provider<? extends Directory> mavenizerOutput;
    private final Property<MinecraftMappings> mappings;
    private @Nullable String sourceSetName;

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public MinecraftDependencyImpl(Provider<? extends Directory> mavenizerOutput) {
        this.mavenizerOutput = mavenizerOutput;
        this.mappings = this.getObjects().property(MinecraftMappings.class).convention(
            ((MinecraftExtensionImpl) getProject().getExtensions().getByType(MinecraftExtension.class)).mappings
        );
    }

    @Override
    public ExternalModuleDependency asDependency() {
        return this.delegate;
    }

    @Override
    public TaskProvider<SyncMavenizer> asTask() {
        return this.mavenizer;
    }

    @Override
    public ExternalModuleDependency init(Object dependencyNotation, Closure<?> closure) {
        var dependency = (ExternalModuleDependency) getProject().getDependencies().create(dependencyNotation, Closures.<Dependency, ExternalModuleDependency>function(d -> {
            if (!(d instanceof ExternalModuleDependency module))
                throw this.problems.invalidMinecraftDependencyType(d);

            if (module.isChanging())
                throw this.problems.changingMinecraftDependency(module);

            Closures.invoke(this.closure(closure), module);

            ((ExtensionAware) module).getExtensions().getExtraProperties().set(MC_EXT_NAME, this);

            return module;
        }));

        this.mavenizer = SyncMavenizer.register(getProject(), dependency, this.mappings, mavenizerOutput);

        return this.delegate = dependency;
    }

    @Override
    public Action<? super AttributeContainer> addAttributes() {
        return attributes -> {
            attributes.attribute(MinecraftExtension.Attributes.os, this.getObjects().named(OperatingSystemFamily.class, OperatingSystem.current().getFamilyName()));
            attributes.attributeProvider(MinecraftExtension.Attributes.mappingsChannel, mappings.map(MinecraftMappings::channel));
            attributes.attributeProvider(MinecraftExtension.Attributes.mappingsVersion, mappings.map(MinecraftMappings::version));
        };
    }

    @Override
    public void handle(Configuration configuration) {
        if (!configuration.isCanBeResolved()) return;

        this.mappings.finalizeValue();
        var dependency = this.asDependency();
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var moduleSelector = "%s:%s".formatted(dependency.getModule(), dependency.getVersion());
            var module = s.module(moduleSelector);
            try {
                s.substitute(module)
                 .using(s.variant(module, variant -> variant.attributes(this.addAttributes())))
                 .because("Accounts for mappings used and natives variants");
            } catch (InvalidUserCodeException e) {
                throw new IllegalStateException("Resolvable configuration '%s' was resolved too early!".formatted(configuration.getName()), e);
            }
        });

        var hierarchy = configuration.getHierarchy();

        var sourceSet = Util.getSourceSet(
            getProject().getConfigurations().matching(hierarchy::contains),
            getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets(),
            this.asDependency()
        );

        // Hope that this is handled elsewhere, and that we are transitive.
        if (sourceSet != null) {
            this.handle(sourceSet);
        }
    }

    @Override
    public void handle(SourceSet sourceSet) {
        getProject().getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            task.doFirst(t -> {
                var file = this.mavenizerOutput.map(dir -> dir.dir(Util.pathify(asDependency()))).get().getAsFile();
                if (!file.exists())
                    throw this.problems.mavenizerOutOfDateCompile(asDependency());
            });
        });

        if (this.sourceSetName != null) {
            if (!this.sourceSetName.equals(sourceSet.getName())) {
                throw new IllegalStateException("MinecraftDependency '%s' has already been handled!".formatted(this.asDependency()));
            }

            return;
        }

        this.sourceSetName = sourceSet.getName();
    }

    @Override
    public MinecraftMappings getMappings() {
        try {
            return this.mappings.get();
        } catch (IllegalStateException e) {
            throw this.problems.missingMappings(e);
        }
    }

    @Override
    @NamedVariant
    public void mappings(String channel, String version) {
        // manual null-checks here instead of @NullCheck for enhanced problems reporting
        MinecraftMappings.checkParam(this.problems, channel, "channel");
        MinecraftMappings.checkParam(this.problems, version, "version");

        this.mappings.set(new MinecraftMappings(channel, version));
    }

    @Override
    public void mappings(
        @NamedParams({
            @NamedParam(
                type = String.class,
                value = "channel",
                required = true
            ),
            @NamedParam(
                type = String.class,
                value = "version",
                required = true
            )
        }) Map<?, ?> namedArgs
    ) {
        this.mappings(namedArgs.get("channel").toString(), namedArgs.get("version").toString());
    }

    static abstract class WithAccessTransformersImpl extends MinecraftDependencyImpl implements WithAccessTransformers {
        private final RegularFileProperty atFile = this.getObjects().fileProperty();
        private final Property<String> atPath = this.getObjects().property(String.class)
            .convention(getProject().getExtensions().getByType(MinecraftExtensionForProjectWithAccessTransformers.class).getAccessTransformers());

        private final Attribute<Boolean> attribute = this.registerTransform();

        @Inject
        public WithAccessTransformersImpl(Provider<? extends Directory> mavenizerOutput) {
            super(mavenizerOutput);
        }

        @Override
        public Action<? super AttributeContainer> addAttributes() {
            return attributes -> {
                super.addAttributes().execute(attributes);
                attributes.attribute(this.attribute, true);
            };
        }

        @Override
        public void handle(SourceSet sourceSet) {
            super.handle(sourceSet);

            if (!Util.contains(getProject().getConfigurations(), sourceSet, false, this.asDependency())) return;
            if (!this.atPath.isPresent()) return;

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                this.atFile.convention(this.getProjectLayout().file(this.getProviders().provider(() -> new File(itor.next(), this.atPath.get()))).get());
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                this.atFile.convention(this.getProjectLayout().getProjectDirectory().file(this.getProviders().provider(() -> "src/%s/resources/%s".formatted(sourceSet.getName(), this.atPath.get()))).get());
            }

            ArtifactAccessTransformer.validateConfig(getProject(), this.asDependency(), this.atFile);
        }

        private Attribute<Boolean> registerTransform() {
            var dependencies = getProject().getDependencies();

            var attribute = Attribute.of("net.minecraftforge.gradle.accesstransformers.automatic." + this.getIndex(), Boolean.class);

            dependencies.attributesSchema(attributesSchema -> attributesSchema.attribute(attribute));

            dependencies.getArtifactTypes().named(
                ArtifactTypeDefinition.JAR_TYPE,
                type -> type.getAttributes().attribute(attribute, false)
            );

            dependencies.registerTransform(ArtifactAccessTransformer.class, spec -> {
                spec.parameters(ArtifactAccessTransformer.Parameters.defaults(getProject(), parameters -> {
                    parameters.getConfig().set(this.atFile);
                }));

                spec.getFrom()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                    .attribute(Category.CATEGORY_ATTRIBUTE, this.getObjects().named(Category.class, Category.LIBRARY))
                    .attribute(attribute, false);

                spec.getTo()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                    .attribute(Category.CATEGORY_ATTRIBUTE, this.getObjects().named(Category.class, Category.LIBRARY))
                    .attribute(attribute, true);
            });

            return attribute;
        }

        private int getIndex() {
            var ext = getProject().getGradle().getExtensions().getExtraProperties();

            int index = ext.has(AT_COUNT_NAME)
                ? (int) Objects.requireNonNull(ext.get(AT_COUNT_NAME), "Internal extra property can never be null!") + 1
                : 0;
            ext.set(AT_COUNT_NAME, index);
            return index;
        }

        @Override
        public RegularFileProperty getAccessTransformer() {
            return this.atFile;
        }

        @Override
        public void setAccessTransformer(String accessTransformer) {
            this.atPath.set(accessTransformer);
        }

        @Override
        public void setAccessTransformer(boolean accessTransformer) {
            if (accessTransformer)
                this.setAccessTransformer(MinecraftDependencyWithAccessTransformers.DEFAULT_PATH);
            else
                this.atPath.unsetConvention().unset();
        }
    }
}

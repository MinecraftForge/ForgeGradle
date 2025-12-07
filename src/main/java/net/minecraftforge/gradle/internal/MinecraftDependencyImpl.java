/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.transform.NamedVariant;
import net.minecraftforge.accesstransformers.gradle.ArtifactAccessTransformer;
import net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers;
import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProjectWithAccessTransformers;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradleutils.shared.Closures;
import net.minecraftforge.util.os.OS;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;
import java.util.Set;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    private transient @Nullable("configuration cache") ExternalModuleDependency delegate;
    private transient @Nullable("configuration cache") TaskProvider<SyncMavenizer> mavenizer;

    final Property<String> asString = getObjects().property(String.class);
    final Property<String> asPath = getObjects().property(String.class);
    final Property<ModuleIdentifier> module = getObjects().property(ModuleIdentifier.class);
    final Property<String> version = getObjects().property(String.class);

    private final Provider<? extends Directory> mavenizerOutput;
    private final Property<MinecraftMappingsImpl> mappings;
    private @Nullable String sourceSetName;

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject Project getProject();

    protected abstract @Inject FlowScope getFlowScope();

    protected abstract @Inject FlowProviders getFlowProviders();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public MinecraftDependencyImpl(Provider<? extends Directory> mavenizerOutput) {
        this.mavenizerOutput = mavenizerOutput;
        this.mappings = this.getObjects().property(MinecraftMappingsImpl.class).convention(
            ((MinecraftExtensionImpl) getProject().getExtensions().getByType(MinecraftExtension.class)).mappings
        );
    }

    @Override
    public @Nullable("configuration cache") ExternalModuleDependency asDependency() {
        return this.delegate;
    }

    @Override
    public @Nullable("configuration cache") TaskProvider<SyncMavenizer> asTask() {
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

        this.asString.set(dependency.toString());
        this.asPath.set(Util.pathify(dependency));
        this.module.set(dependency.getModule());
        this.version.set(dependency.getVersion());

        return this.delegate = dependency;
    }

    @Override
    public Action<? super AttributeContainer> addAttributes() {
        return attributes -> {
            attributes.attributeProvider(MinecraftExtensionInternal.AttributesInternal.OS, getProviders().of(OperatingSystemName.class, spec -> spec.parameters(parameters -> parameters.getAllowedOperatingSystems().set(Set.of(OS.WINDOWS, OS.MACOS, OS.LINUX)))));
            attributes.attributeProvider(MinecraftExtensionInternal.AttributesInternal.MAPPINGS_CHANNEL, mappings.map(MinecraftMappings::getChannel));
            attributes.attributeProvider(MinecraftExtensionInternal.AttributesInternal.MAPPINGS_VERSION, mappings.map(MinecraftMappings::getVersion));
        };
    }

    @Override
    public void handle(Configuration configuration) {
        if (!configuration.isCanBeResolved()) return;

        this.mappings.finalizeValue();
        configuration.getResolutionStrategy().dependencySubstitution(s -> {
            var moduleSelector = "%s:%s".formatted(this.module.get(), this.version.get());
            var module = s.module(moduleSelector);
            try {
                s.substitute(module)
                 .using(s.variant(module, variant -> variant.attributes(this.addAttributes())))
                 .because("Accounts for mappings used and natives variants");
            } catch (InvalidUserCodeException e) {
                throw new IllegalStateException("Resolvable configuration '%s' was resolved too early!".formatted(configuration.getName()), e);
            }
        });

        var asDependency = this.asDependency();
        if (asDependency == null) return;

        var hierarchy = configuration.getHierarchy();

        var sourceSet = Util.getSourceSet(
            getProject().getConfigurations().matching(hierarchy::contains),
            getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets(),
            asDependency
        );

        // Hope that this is handled elsewhere, and that we are transitive.
        if (sourceSet != null) {
            this.handle(sourceSet);
        }
    }

    @Override
    public void handle(SourceSet sourceSet) {
        var asString = this.asString.get();
        var dependencyOutput = this.mavenizerOutput.map(dir -> dir.dir(this.asPath)).get().get().getAsFile();
        getFlowScope().always(ForgeGradleFlowAction.MavenizerSyncCheck.class, spec -> {
            spec.parameters(parameters -> {
                parameters.getFailure().set(getFlowProviders().getBuildWorkResult().map(r -> r.getFailure().orElse(null)));
                parameters.dependencyOutput.set(dependencyOutput);
                parameters.dependency.set(asString);
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
        this.mappings.set(this.getObjects().newInstance(MinecraftMappingsImpl.class, channel, version));
    }

    static abstract class WithAccessTransformersImpl extends MinecraftDependencyImpl implements WithAccessTransformers {
        private final RegularFileProperty atFile = this.getObjects().fileProperty();
        private final Property<String> atPath = this
            .getObjects().property(String.class)
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

            var asDependency = this.asDependency();
            if (asDependency == null) return;

            if (!Util.contains(getProject().getConfigurations(), sourceSet, false, asDependency)) return;
            if (!this.atPath.isPresent()) return;

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                this.atFile.convention(this.getProjectLayout().file(this.getProviders().provider(() -> new File(itor.next(), this.atPath.get()))).get());
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                this.atFile.convention(this.getProjectLayout().getProjectDirectory().file(this.getProviders().provider(() -> "src/%s/resources/%s".formatted(sourceSet.getName(), this.atPath.get()))).get());
            }

            ArtifactAccessTransformer.validateConfig(getProject(), asDependency, this.atFile);
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

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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    private static final String HAS_MINECRAFT_DEPENDENCY = "__fg_minecraft_dependency_declared";
    private static final String AT_COUNT_NAME = "__fg_minecraft_atcontainers";

    private @UnknownNullability ExternalModuleDependency delegate;
    private @Nullable MavenizerAction mavenizer;

    private final Property<MinecraftMappings> mappings;

    final Project project;
    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ExecOperations getExecOperations();

    @Inject
    public MinecraftDependencyImpl(Project project) {
        this.project = project;

        this.mappings = this.getObjects().property(MinecraftMappings.class).convention(
            ((MinecraftExtensionImpl) project.getExtensions().getByName(MinecraftExtension.NAME)).mappings
        );
    }

    @Override
    public ExternalModuleDependency getDelegate() {
        return this.delegate;
    }

    ExternalModuleDependency setDelegate(Object dependencyNotation, Closure<?> closure) {
        {
            var ext = this.project.getGradle().getExtensions().getExtraProperties();
            if (ext.has(HAS_MINECRAFT_DEPENDENCY) && Objects.requireNonNullElse((Boolean) ext.get(HAS_MINECRAFT_DEPENDENCY), false)) {
                // TODO [ForgeGradle][Parallelism] Add warning for Mavenizer parallelism incubation
                this.project.getLogger().warn("WARNING: Declaring multiple Minecraft dependencies is not supported and may lead to build issues and failures!");
            }

            ext.set(HAS_MINECRAFT_DEPENDENCY, true);
        }

        var dependency = (ExternalModuleDependency) this.project.getDependencies().create(dependencyNotation, Closures.<Dependency, ExternalModuleDependency>function(d -> {
            if (!(d instanceof ExternalModuleDependency module))
                throw this.problems.invalidMinecraftDependencyType(d);

            if (module.isChanging())
                throw this.problems.changingMinecraftDependency(module);

            Closures.invoke(this.closure(closure), module);

            return module;
        }));

        var plugin = this.project.getPlugins().getPlugin(ForgeGradlePlugin.class);
        this.mavenizer = new MavenizerAction(this.getObjects(), this.getExecOperations(), mavenizer -> {
            // JavaExec
            var tool = plugin.getTool(Tools.MAVENIZER);
            mavenizer.classpath.convention(tool.getClasspath());
            mavenizer.javaLauncher.convention(tool.getJavaLauncher());
            mavenizer.mainClass.convention(tool.getMainClass());

            // Minecraft Maven
            var defaultDirectory = this.getObjects().directoryProperty().value(plugin.globalCaches().dir("mavenizer").map(this.problems.ensureFileLocation()));
            mavenizer.caches.convention(defaultDirectory.dir("cache").map(this.problems.ensureFileLocation()));
            mavenizer.output.convention(defaultDirectory.dir("output").map(this.problems.ensureFileLocation()));

            // Dependency
            mavenizer.module.set(dependency.getModule().toString());
            mavenizer.version.set(dependency.getVersion());
            mavenizer.mappings.set(this.mappings);

            this.mappings.finalizeValue();
        });

        return this.delegate = dependency;
    }

    RegularFile getMetadataZip() {
        try {
            var mavenizer = Objects.requireNonNull(this.mavenizer);
            mavenizer.releaseLog();
            return mavenizer.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    void resolve() {
        this.getMetadataZip();
    }

    Action<? super AttributeContainer> addAttributes() {
        return attributes -> {
            attributes.attribute(MinecraftExtension.Attributes.os, this.getObjects().named(OperatingSystemFamily.class, OperatingSystem.current().getFamilyName()));
            attributes.attributeProvider(MinecraftExtension.Attributes.mappingsChannel, mappings.map(MinecraftMappings::channel));
            attributes.attributeProvider(MinecraftExtension.Attributes.mappingsVersion, mappings.map(MinecraftMappings::version));
        };
    }

    @Override
    public void handle(SourceSet sourceSet) {
        var configurations = this.project.getConfigurations();
        var dependency = this.getDelegate();

        Util.forEachClasspath(configurations, sourceSet, configuration ->
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
            })
        );
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
        private final Property<String> atPath = this.getObjects().property(String.class);

        private final Attribute<Boolean> attribute;

        @Inject
        public WithAccessTransformersImpl(Project project) {
            super(project);
            this.attribute = this.registerTransform();

            this.atPath.convention(project.getExtensions().getByType(MinecraftExtensionForProjectWithAccessTransformers.class).getAccessTransformers());
        }

        @Override
        Action<? super AttributeContainer> addAttributes() {
            return attributes -> {
                super.addAttributes().execute(attributes);
                attributes.attribute(this.attribute, true);
            };
        }

        @Override
        public void handle(SourceSet sourceSet) {
            super.handle(sourceSet);

            if (!Util.contains(project.getConfigurations(), sourceSet, false, this.getDelegate())) return;
            if (!this.atPath.isPresent()) return;

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                this.atFile.convention(this.getProjectLayout().file(this.getProviders().provider(() -> new File(itor.next(), this.atPath.get()))).get());
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                this.atFile.convention(this.getProjectLayout().getProjectDirectory().file(this.getProviders().provider(() -> "src/%s/resources/%s".formatted(sourceSet.getName(), this.atPath.get()))).get());
            }
        }

        private Attribute<Boolean> registerTransform() {
            var dependencies = this.project.getDependencies();

            var attribute = Attribute.of("net.minecraftforge.gradle.accesstransformers.automatic." + this.getIndex(), Boolean.class);

            dependencies.attributesSchema(attributesSchema -> attributesSchema.attribute(attribute));

            dependencies.getArtifactTypes().named(
                ArtifactTypeDefinition.JAR_TYPE,
                type -> type.getAttributes().attribute(attribute, false)
            );

            dependencies.registerTransform(ArtifactAccessTransformer.class, spec -> {
                spec.parameters(ArtifactAccessTransformer.Parameters.defaults(project, parameters -> {
                    parameters.getConfig().set(this.atFile);
                    this.project.afterEvaluate(p ->
                        ArtifactAccessTransformer.validateConfig(this.project, this.getDelegate(), this.atFile)
                    );
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
            var ext = this.project.getGradle().getExtensions().getExtraProperties();

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

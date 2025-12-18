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
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftExtensionForProjectWithAccessTransformers;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
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
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;
import java.util.Set;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    // These can be nullable due to configuration caching.
    private transient @Nullable ExternalModuleDependency delegate;
    private transient @Nullable TaskProvider<SyncMavenizer> mavenizer;
    private transient @Nullable NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs;

    private final MinecraftExtensionImpl.ForProjectImpl<?> minecraft;

    final Property<String> asString = getObjects().property(String.class);
    final Property<String> asPath = getObjects().property(String.class);
    final Property<ModuleIdentifier> module = getObjects().property(ModuleIdentifier.class);
    final Property<String> version = getObjects().property(String.class);

    private final DirectoryProperty mavenizerOutput = getObjects().directoryProperty();
    private final Property<MinecraftMappingsImpl> mappings = this.getObjects().property(MinecraftMappingsImpl.class);
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
        this.minecraft = ((MinecraftExtensionImpl.ForProjectImpl<?>) getProject().getExtensions().getByType(MinecraftExtensionForProject.class));

        this.mavenizerOutput.set(mavenizerOutput);
        this.mappings.convention(minecraft.mappings);
    }

    // Can be nullable due to configuration caching.
    @Override
    public @Nullable NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns() {
        return this.runs;
    }

    // Can be nullable due to configuration caching.
    @Override
    public @Nullable ExternalModuleDependency asDependency() {
        return this.delegate;
    }

    // Can be nullable due to configuration caching.
    @Override
    public @Nullable TaskProvider<SyncMavenizer> asTask() {
        return this.mavenizer;
    }

    @Override
    public ExternalModuleDependency init(Object dependencyNotation, Closure<?> closure) {
        this.runs = getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class);

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
        return attributes -> { };
    }

    @Override
    public void handle(Configuration configuration) { }

    @Override
    public void handle(NamedDomainObjectSet<SourceSet> sourceSets, NamedDomainObjectSet<SourceSet> allSourceSets) {
        allSourceSets.all(sourceSet ->
            getProject().getTasks().named(sourceSet.getTaskName("sync", "mavenizer"), task -> task.dependsOn(this.mavenizer))
        );

        var asString = this.asString.get();
        var dependencyOutput = this.mavenizerOutput.dir(this.asPath);
        getFlowScope().always(ForgeGradleFlowAction.MavenizerSyncCheck.class, spec ->
            spec.parameters(parameters -> {
                parameters.getFailure().set(getFlowProviders().getBuildWorkResult().map(r -> r.getFailure().orElse(null)));
                parameters.dependencyOutput.set(dependencyOutput);
                parameters.dependency.set(asString);
            })
        );

        if (!sourceSets.isEmpty() && this.sourceSetName == null)
            this.sourceSetName = sourceSets.iterator().next().getName();

        var runs = Objects.requireNonNullElseGet(getRuns(), () -> getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class));
        ((NamedDomainObjectContainer<SlimeLauncherOptionsImpl>) runs).addAll((NamedDomainObjectContainer<SlimeLauncherOptionsImpl>) minecraft.getRuns());
        allSourceSets.configureEach(sourceSet -> {
            var single = getProject()
                .getConfigurations()
                .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .getAllDependencies()
                .matching(MinecraftDependencyInternal::is)
                .size() == 1;
            runs.forEach(options -> {
                var task = SlimeLauncherExec.register(getProject(), sourceSet, (SlimeLauncherOptionsImpl) options, module.get(), version.get(), asPath.get(), asString, single, minecraft.getEclipseOutputDir());
            });
        });
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
        public void handle(Configuration configuration) {
            super.handle(configuration);

            if (configuration.isCanBeResolved()) {
                configuration.getResolutionStrategy().dependencySubstitution(s -> {
                    var moduleSelector = "%s:%s".formatted(this.module.get(), this.version.get());
                    var module = s.module(moduleSelector);
                    try {
                        s.substitute(module)
                         .using(s.variant(module, variant -> variant.attributes(this.addAttributes())))
                         .because("Applies AccessTransformers");
                    } catch (InvalidUserCodeException e) {
                        throw new IllegalStateException("Resolvable configuration '%s' was resolved too early!".formatted(configuration.getName()), e);
                    }
                });
            }
        }

        @Override
        public void handle(NamedDomainObjectSet<SourceSet> sourceSets, NamedDomainObjectSet<SourceSet> allSourceSets) {
            super.handle(sourceSets, allSourceSets);

            if (!this.atPath.isPresent() || sourceSets.isEmpty()) return;
            var sourceSet = sourceSets.iterator().next();

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                var file = itor.next();
                this.atFile.convention(this.getProjectLayout().file(this.atPath.map(atPath -> new File(file, atPath))));
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                var sourceSetName = sourceSet.getName();
                this.atFile.convention(this.getProjectLayout().getProjectDirectory().file(this.atPath.map(atPath -> "src/" + sourceSetName + "/resources/" + atPath)));
            }

            ArtifactAccessTransformer.validateConfig(getProject(), asDependency(), this.atFile);
        }

        private Attribute<Boolean> registerTransform() {
            var dependencies = getProject().getDependencies();

            var attribute = Attribute.of("net.minecraftforge.gradle.accesstransformers.automatic." + this.getIndex(), Boolean.class);

            dependencies.getAttributesSchema().attribute(attribute);
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
                    .attribute(Category.CATEGORY_ATTRIBUTE, spec.getFrom().named(Category.class, Category.LIBRARY))
                    .attribute(attribute, false);

                spec.getTo()
                    .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                    .attribute(Category.CATEGORY_ATTRIBUTE, spec.getTo().named(Category.class, Category.LIBRARY))
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

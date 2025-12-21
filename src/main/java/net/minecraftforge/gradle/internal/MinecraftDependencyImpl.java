/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.transform.NamedVariant;
import net.minecraftforge.accesstransformers.gradle.ArtifactAccessTransformer;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
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
import org.gradle.api.attributes.Category;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    // These can be nullable due to configuration caching.
    private transient @Nullable ExternalModuleDependency delegate;
    private transient @Nullable TaskProvider<SyncMavenizer> mavenizer;
    private transient @Nullable NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs;

    // Minecraft extension
    private final MinecraftExtensionInternal.ForProject minecraft = ((MinecraftExtensionInternal.ForProject) getProject().getExtensions().getByType(MinecraftExtensionForProject.class));

    // Access Transformers
    private final RegularFileProperty accessTransformer = this.getObjects().fileProperty().convention(minecraft.getAccessTransformer());
    private final Property<String> accessTransformerPath = this.getObjects().property(String.class).convention(minecraft.getAccessTransformerPath());

    // Dependency Information
    final Property<String> asString = getObjects().property(String.class);
    final Property<String> asPath = getObjects().property(String.class);
    final Property<ModuleIdentifier> module = getObjects().property(ModuleIdentifier.class);
    final Property<String> version = getObjects().property(String.class);

    private final DirectoryProperty mavenizerOutput = getObjects().directoryProperty();
    private final Property<MinecraftMappingsInternal> mappings = this.getObjects().property(MinecraftMappingsInternal.class);
    private @Nullable String sourceSetName;

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject Project getProject();

    protected abstract @Inject FlowScope getFlowScope();

    protected abstract @Inject FlowProviders getFlowProviders();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    @Inject
    public MinecraftDependencyImpl(Provider<? extends Directory> mavenizerOutput) {
        this.mavenizerOutput.set(mavenizerOutput);
        this.mappings.convention(minecraft.getMappingsProperty());
    }

    // Can be nullable due to configuration caching.
    @Override
    public @Nullable NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns() {
        return this.runs;
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

    @Override public boolean hasAccessTransformersPlugin() {
        return minecraft.hasAccessTransformersPlugin();
    }

    @Override
    public RegularFileProperty getAccessTransformer() {
        return this.accessTransformer;
    }

    @Override
    public Property<String> getAccessTransformerPath() {
        return this.accessTransformerPath;
    }

    /* INTERNAL */

    private boolean hasAccessTransformers() {
        return this.accessTransformer.isPresent() || this.accessTransformerPath.isPresent();
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
    public void handle(Configuration configuration) {
        if (configuration.isCanBeResolved()) {
            var moduleSelector = "%s:%s".formatted(this.module.get(), this.version.get());
            var resolutionStrategy = configuration.getResolutionStrategy();
            var dependencySubstitution = resolutionStrategy.getDependencySubstitution();

            // Force the version we have in our configuration.
            resolutionStrategy.force(moduleSelector);

            // Apply the dependency substitution for Access Transformers.
            if (this.hasAccessTransformers()) {
                var module = dependencySubstitution.module(moduleSelector);
                try {
                    dependencySubstitution
                        .substitute(module)
                        .using(dependencySubstitution.variant(module, variant -> variant.attributes(attributes ->
                            attributes.attribute(this.registerAccessTransformer(), Boolean.TRUE)))
                        )
                        .because("Applies AccessTransformers");
                } catch (InvalidUserCodeException e) {
                    throw new IllegalStateException("Resolvable configuration '%s' was resolved too early!".formatted(configuration.getName()), e);
                }
            }
        }
    }

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

        var runs = Objects.requireNonNullElseGet(this.getRuns(), () -> getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class));
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

        if (this.accessTransformer.isPresent() && this.accessTransformerPath.isPresent() && !sourceSets.isEmpty()) {
            var sourceSet = sourceSets.iterator().next();

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                var file = itor.next();
                this.accessTransformer.convention(this.getProjectLayout().file(this.accessTransformerPath.map(atPath -> new File(file, atPath))));
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                var sourceSetName = sourceSet.getName();
                this.accessTransformer.convention(this.getProjectLayout().getProjectDirectory().file(this.accessTransformerPath.map(atPath -> "src/" + sourceSetName + "/resources/" + atPath)));
            }

            ArtifactAccessTransformer.validateConfig(getProject(), asDependency(), this.accessTransformer);
        }
    }

    private Attribute<Boolean> registerAccessTransformer() {
        var dependencies = getProject().getDependencies();

        var attribute = Attribute.of("net.minecraftforge.gradle.accesstransformers.automatic." + this.getATIndex(), Boolean.class);

        dependencies.getAttributesSchema().attribute(attribute);
        dependencies.getArtifactTypes().named(
            ArtifactTypeDefinition.JAR_TYPE,
            type -> type.getAttributes().attribute(attribute, false)
        );

        dependencies.registerTransform(ArtifactAccessTransformer.class, spec -> {
            spec.parameters(ArtifactAccessTransformer.Parameters.defaults(getProject(), parameters -> {
                parameters.getConfig().set(this.accessTransformer);
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

    private int getATIndex() {
        var ext = getProject().getGradle().getExtensions().getExtraProperties();

        int index = ext.has(AT_COUNT_NAME)
            ? (int) Objects.requireNonNull(ext.get(AT_COUNT_NAME), "Internal extra property can never be null!") + 1
            : 0;
        ext.set(AT_COUNT_NAME, index);
        return index;
    }
}

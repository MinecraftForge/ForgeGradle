/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.transform.NamedVariant;
import net.minecraftforge.gradle.MavenizerInstance;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Objects;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    // These can be nullable due to configuration caching.
    private transient @Nullable ExternalModuleDependency delegate;
    private transient @Nullable NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs;

    // Minecraft extension
    private final MinecraftExtensionInternal.ForProject minecraft = ((MinecraftExtensionInternal.ForProject) getProject().getExtensions().getByType(MinecraftExtensionForProject.class));
    private final String mavenizerName; // Name for our mavenizer invocation in the Minecraft Extension
    private @Nullable Configuration detatchedConfig;
    private @Nullable Configuration metadataConfig;
    private @Nullable TaskProvider<SlimeLauncherMetadata> metadataTask;

    // Access Transformers
    private final ConfigurableFileCollection accessTransformer = this.getObjects().fileCollection();
    private final Property<String> accessTransformerPath = this.getObjects().property(String.class);

    // Dependency Information
    private final Property<String> asString = getObjects().property(String.class);
    private final Property<String> asPath = getObjects().property(String.class);
    private final Property<ModuleIdentifier> module = getObjects().property(ModuleIdentifier.class);
    private final Property<String> version = getObjects().property(String.class);

    private final Property<MinecraftMappingsInternal> mappings = this.getObjects().property(MinecraftMappingsInternal.class);

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject Project getProject();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    @Inject
    public MinecraftDependencyImpl(String mavenizerName) {
        this.mavenizerName = mavenizerName;
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

    @Override
    public MavenizerInstance getMavenizerInstance() {
        return this.minecraft.getDependency(this.mavenizerName);
    }

    @Override
    public FileCollection getMinecraftDependencies() {
        assert this.detatchedConfig != null;
        return this.detatchedConfig;
    }

    @Override
    public FileCollection getMetadataDependency() {
        assert this.metadataConfig != null;
        return this.metadataConfig;
    }

    @Override
    public TaskProvider<SlimeLauncherMetadata> getMetadataTask() {
        if (this.metadataTask == null)
            this.metadataTask = SlimeLauncherMetadata.register(this.getProject(), this);
        return this.metadataTask;
    }

    @Override public boolean hasAccessTransformersPlugin() {
        return minecraft.hasAccessTransformersPlugin();
    }

    @Override
    public ConfigurableFileCollection getAccessTransformer() {
        return this.accessTransformer;
    }

    @Override
    public Property<String> getAccessTransformerPath() {
        return this.accessTransformerPath;
    }

    /* INTERNAL */

    private boolean hasAccessTransformers() {
        return !this.accessTransformer.isEmpty() || this.accessTransformerPath.isPresent();
    }

    // Can be nullable due to configuration caching.
    @Override
    public @Nullable ExternalModuleDependency asDependency() {
        return this.delegate;
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

        // Keep a standalone configuration of JUST this dependency, so we can fill in 'minecraft_classpath' for run configs.
        this.detatchedConfig = this.getProject().getConfigurations().detachedConfiguration(dependency);
        this.metadataConfig = this.getProject().getConfigurations().detachedConfiguration(getProject().getDependencyFactory().create(
            dependency.getModule().getGroup(), dependency.getModule().getName(), dependency.getVersion(), "metadata", "zip"
        ));
        this.metadataConfig.setTransitive(false);
        this.metadataConfig.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, a.named(Usage.class, "metadata")));

        this.asString.set(dependency.toString());
        this.asPath.set(Util.pathify(dependency));
        this.module.set(dependency.getModule());
        this.version.set(dependency.getVersion());

        return this.delegate = dependency;
    }

    @Override
    public String toString() {
        return this.asString.getOrElse(super.toString());
    }

    @Override
    public String getPath() {
        return this.asPath.get();
    }

    @Override
    public ModuleIdentifier getModule() {
        return this.module.get();
    }

    @Override
    public void handle(Configuration configuration) {
        if (configuration.isCanBeResolved()) {
            var moduleSelector = "%s:%s".formatted(this.module.get(), this.version.get());
            var resolutionStrategy = configuration.getResolutionStrategy();
            var dependencySubstitution = resolutionStrategy.getDependencySubstitution();

            // Apply the dependency substitution for mappings attributes.
            if (this.mappings.isPresent()) {
                var instance = this.getMavenizerInstance();
                var module = dependencySubstitution.module(moduleSelector);
                try {
                    dependencySubstitution
                        .substitute(module)
                        .using(dependencySubstitution.variant(module, variant -> variant.attributes(attributes -> {
                            attributes.attributeProvider(ForgeAttributes.MappingsChannel.ATTRIBUTE, instance.getMappingChannel());
                            attributes.attributeProvider(ForgeAttributes.MappingsVersion.ATTRIBUTE, instance.getMappingVersion());
                        })))
                        .because("Accounts for declared mappings.");
                } catch (InvalidUserCodeException e) {
                    throw new IllegalStateException("Resolvable configuration '%s' was resolved too early!".formatted(configuration.getName()), e);
                }
            }
        }
    }

    @Override
    public void handle(NamedDomainObjectSet<SourceSet> sourceSets, NamedDomainObjectSet<SourceSet> allSourceSets) {
        var runs = Objects.requireNonNullElseGet(this.getRuns(), () -> getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class));
        ((NamedDomainObjectContainer<SlimeLauncherOptionsImpl>) runs).addAll((NamedDomainObjectContainer<SlimeLauncherOptionsImpl>) minecraft.getRuns());

        allSourceSets.configureEach(sourceSet -> {
            runs.forEach(options -> {
                var runTask = SlimeLauncherExec.register(getProject(), sourceSet, (SlimeLauncherOptionsImpl)options, this);
            });
        });

        finalizeAccessTransformers(sourceSets);
    }

    void finalizeAccessTransformers(NamedDomainObjectSet<SourceSet> sourceSets) {
        if (this.accessTransformer.isEmpty() && !this.accessTransformerPath.isPresent()) {
            this.accessTransformer.convention(minecraft.getAccessTransformer());
            this.accessTransformerPath.convention(minecraft.getAccessTransformerPath());
        }

        if (this.accessTransformer.isEmpty() && this.accessTransformerPath.isPresent() && !sourceSets.isEmpty()) {
            var sourceSet = sourceSets.iterator().next();

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                var file = itor.next();
                this.accessTransformer.setFrom(this.getProjectLayout().file(this.accessTransformerPath.map(atPath -> new File(file, atPath))));
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                var sourceSetName = sourceSet.getName();
                this.accessTransformer.setFrom(this.getProjectLayout().getProjectDirectory().file(this.accessTransformerPath.map(atPath -> "src/" + sourceSetName + "/resources/" + atPath)));
            }
        }
    }
}

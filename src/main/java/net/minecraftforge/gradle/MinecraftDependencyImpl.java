/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import groovy.transform.NamedVariant;
import net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyScopeConfiguration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtraPropertiesExtension.UnknownPropertyException;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.OperatingSystemFamily;
import org.jetbrains.annotations.UnknownNullability;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

abstract class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    private static final String AT_COUNT_NAME = "fg_minecraft_atcontainers";

    private @UnknownNullability Provider<ExternalModuleDependency> delegate;

    private final Property<MinecraftMappings> mappings;

    final Project project;
    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject ProviderFactory getProviders();

    @Inject
    public MinecraftDependencyImpl(Project project) {
        this.project = project;

        this.mappings = this.getObjects().property(MinecraftMappings.class).convention(
            ((MinecraftExtensionImpl) project.getExtensions().getByName(MinecraftExtension.NAME)).mappings
        );
    }

    @Override
    public Provider<ExternalModuleDependency> getDelegate() {
        return this.delegate;
    }

    Provider<ExternalModuleDependency> setDelegate(Object dependencyNotation, Closure<?> closure) {
        return this.delegate = this.getObjects().property(ExternalModuleDependency.class).value(this.getProviders().provider(
            () -> (ExternalModuleDependency) this.project.getDependencies().create(dependencyNotation, Closures.<Dependency, ExternalModuleDependency>function(dependency -> {
                if (!(dependency instanceof ExternalModuleDependency module))
                    throw this.problems.invalidMinecraftDependencyType(dependency);

                if (module.isChanging())
                    throw this.problems.changingMinecraftDependency(dependency);

                Closures.invoke(this.closure(closure), module);

                return module;
            }))
        ));
    }

    void resolve() {
        this.getDelegate().get();
    }

    @Override
    public void handle(SourceSet sourceSet) {
        var configurations = this.project.getConfigurations();
        var dependency = this.getDelegate().get();

        NamedDomainObjectProvider<DependencyScopeConfiguration> minecraftDependencyConstraints;
        try {
            minecraftDependencyConstraints = configurations.dependencyScope("minecraftDependencyConstraints", configuration -> {
                configuration.setDescription("Transient dependency constraints for Minecraft dependencies.");
            });
        } catch (InvalidUserDataException e) {
            minecraftDependencyConstraints = configurations.named("minecraftDependencyConstraints", DependencyScopeConfiguration.class);
        }

        configurations.getByName(sourceSet.getCompileClasspathConfigurationName()).extendsFrom(minecraftDependencyConstraints.get());
        configurations.getByName(sourceSet.getRuntimeClasspathConfigurationName()).extendsFrom(minecraftDependencyConstraints.get());

        minecraftDependencyConstraints.get().getDependencyConstraints().add(this.project.getDependencies().getConstraints().create(dependency.getModule().toString(), constraint -> {
            constraint.because("Accounts for mappings used and natives variants");

            constraint.attributes(attributes -> {
                attributes.attribute(MinecraftExtension.Attributes.os, this.getObjects().named(OperatingSystemFamily.class, OperatingSystem.current().getFamilyName()));
                attributes.attributeProvider(MinecraftExtension.Attributes.mappingsChannel, mappings.map(MinecraftMappings::channel));
                attributes.attributeProvider(MinecraftExtension.Attributes.mappingsVersion, mappings.map(MinecraftMappings::version));
            });
        }));
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
        private final AccessTransformersContainer atContainer;
        private final Property<String> atPath = this.getObjects().property(String.class);

        private @UnknownNullability Provider<ExternalModuleDependency> delegate;

        @Inject
        public WithAccessTransformersImpl(Project project) {
            super(project);
            this.atContainer = AccessTransformersContainer.register(
                project, Attribute.of("net.minecraftforge.gradle.accesstransformed." + this.postIncrementContainerCount(), Boolean.class), it -> { }
            );
            this.atPath.convention(project.getExtensions().getByType(MinecraftExtensionForProjectWithAccessTransformers.class).getAccessTransformers());
        }

        @Override
        public Provider<ExternalModuleDependency> getDelegate() {
            return this.delegate;
        }

        @Override
        Provider<ExternalModuleDependency> setDelegate(Object dependencyNotation, Closure<?> closure) {
            var dependency = super.setDelegate(dependencyNotation, closure);
            return this.delegate = this.atPath.map(path -> (ExternalModuleDependency) this.atContainer.dep(dependency).get()).orElse(dependency);
        }

        @Override
        public void handle(SourceSet sourceSet) {
            super.handle(sourceSet);

            if (!Util.contains(project.getConfigurations(), sourceSet, false, this.getDelegate().get())) return;
            if (!this.atPath.isPresent()) return;

            var itor = sourceSet.getResources().getSrcDirs().iterator();
            if (itor.hasNext()) {
                this.getAccessTransformer().convention(this.getProjectLayout().file(this.getProviders().provider(() -> new File(itor.next(), this.atPath.get()))).get());
            } else {
                // weird edge case where a source set might not have any resources???
                // in which case, just best guess the location for accesstransformer.cfg
                this.getAccessTransformer().convention(this.getProjectLayout().getProjectDirectory().file(this.getProviders().provider(() -> "src/%s/resources/%s".formatted(sourceSet.getName(), this.atPath.get()))).get());
            }
        }

        private int getAtContainerCount() {
            try {
                //noinspection DataFlowIssue
                return (int) this.project.getExtensions().getExtraProperties().get(AT_COUNT_NAME);
            } catch (UnknownPropertyException ignored) {
                this.setAtContainerCount(0);
                return 0;
            }
        }

        private void setAtContainerCount(int count) {
            this.project.getExtensions().getExtraProperties().set(AT_COUNT_NAME, count);
        }

        private int postIncrementContainerCount() {
            int ret = this.getAtContainerCount();
            this.setAtContainerCount(ret + 1);
            return ret;
        }

        @Override
        public RegularFileProperty getAccessTransformer() {
            return this.atContainer.getOptions().getConfig();
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

        @Override
        public void accessTransformer(Action<? super AccessTransformersContainer.Options> options) {
            this.atContainer.options(options);
        }
    }
}

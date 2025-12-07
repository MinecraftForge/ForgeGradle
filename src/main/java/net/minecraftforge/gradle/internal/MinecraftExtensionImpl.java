/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.CompileStatic;
import groovy.transform.NamedVariant;
import groovy.transform.PackageScope;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.gradle.ClosureOwner;
import net.minecraftforge.gradle.MinecraftDependency;
import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

abstract class MinecraftExtensionImpl implements MinecraftExtensionInternal {
    private static final String EXT_MAVEN_REPOS = "fg_mc_maven_repos";
    private static final String EXT_MAPPINGS = "fg_mc_mappings";

    final ForgeGradleProblems problems;

    // MCMaven
    final DirectoryProperty mavenizerOutput;

    // Dependencies
    final Property<MinecraftMappingsImpl> mappings;

    protected abstract @Inject ObjectFactory getObjects();

    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        var extensions = target.getExtensions();
        if (target instanceof Project project) {
            if (project.getPluginManager().hasPlugin("net.minecraftforge.accesstransformers")) {
                try {
                    extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForProjectImpl.WithAccessTransformersImpl.class, plugin);
                } catch (Exception e) {
                    var problems = project.getObjects().newInstance(ForgeGradleProblems.class);
                    throw problems.accessTransformersNotOnClasspath(e);
                }
            } else {
                extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForProjectImpl.class, plugin);
            }
        } else if (target instanceof Settings) {
            extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForSettingsImpl.class, plugin, target);
        } else {
            extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.class, plugin);
        }
    }

    @Inject
    public MinecraftExtensionImpl(ForgeGradlePlugin plugin) {
        this.problems = this.getObjects().newInstance(ForgeGradleProblems.class);

        this.mavenizerOutput = this.getObjects().directoryProperty().convention(plugin.localCaches().dir("mavenizer/output").map(this.problems.ensureFileLocation()));

        this.mappings = this.getObjects().property(MinecraftMappingsImpl.class);
    }

    @Override
    public TypeOf<?> getPublicType() {
        return MinecraftExtensionInternal.super.getPublicType();
    }

    @Override
    public Action<MavenArtifactRepository> getMavenizer() {
        return maven -> {
            maven.setName("MinecraftMavenizer");
            maven.setUrl(this.mavenizerOutput.getAsFile());
        };
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
        var replacement = this.getObjects().newInstance(MinecraftMappingsImpl.class, channel, version);
        if (this.mappings.isPresent())
            this.problems.reportOverriddenMappings(this.mappings.get(), replacement);

        this.mappings.set(replacement);
    }

    @CompileStatic
    @PackageScope
    static abstract class ForSettingsImpl extends MinecraftExtensionImpl {
        @Inject
        public ForSettingsImpl(ForgeGradlePlugin plugin, Settings settings) {
            super(plugin);
            settings.getGradle().settingsEvaluated(this::finish);
        }

        private void finish(Settings settings) {
            if (!this.mappings.isPresent()) return;
            var repositories = settings.getDependencyResolutionManagement().getRepositories().withType(MavenArtifactRepository.class);

            var ext = settings.getGradle().getExtensions().getExtraProperties();
            ext.set(EXT_MAVEN_REPOS, repositories);
            ext.set(EXT_MAPPINGS, this.mappings.get());
        }
    }

    static abstract class ForProjectImpl<T extends ClosureOwner & MinecraftDependency & ExternalModuleDependency> extends MinecraftExtensionImpl implements MinecraftExtensionInternal.ForProject<T> {
        // Slime Launcher
        private final NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs;

        // Dependencies
        final List<MinecraftDependencyInternal> minecraftDependencies = new ArrayList<>();

        protected abstract @Inject Project getProject();

        protected abstract @Inject FlowScope getFlowScope();

        protected abstract @Inject FlowProviders getFlowProviders();

        protected abstract @Inject ProjectLayout getProjectLayout();

        protected abstract @Inject ProviderFactory getProviders();

        @Inject
        public ForProjectImpl(ForgeGradlePlugin plugin) {
            super(plugin);
            var project = getProject();

            this.runs = this.getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class);

            var ext = project.getExtensions().getExtraProperties();
            if (ext.has(EXT_MAPPINGS))
                this.mappings.set((MinecraftMappingsImpl) ext.get(EXT_MAPPINGS));

            plugin.queueMessage(ForgeGradleMessage.WELCOME);
            plugin.queueMessage(ForgeGradleMessage.MAGIC);

            this.getFlowScope().always(ForgeGradleFlowAction.AccessTransformersMissing.class, spec -> spec.parameters(parameters -> {
                parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                parameters.appliedPlugin.set(project.getPluginManager().hasPlugin("net.minecraftforge.accesstransformers"));
            }));

            project.getConfigurations()
                   .matching(Configuration::isCanBeResolved)
                   .configureEach(c -> c.withDependencies(d -> this.apply(c)));

            // Finish when the project is evaluated
            project.afterEvaluate(this::finish);
        }

        @Override
        public TypeOf<?> getPublicType() {
            return new TypeOf<MinecraftExtensionForProject<ClosureOwner.MinecraftDependency>>() { };
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<? extends MavenArtifactRepository> getRepositories() {
            var ext = getProject().getGradle().getExtensions().getExtraProperties();
            return ext.has(EXT_MAVEN_REPOS)
                ? Objects.requireNonNull((List<? extends MavenArtifactRepository>) ext.get(EXT_MAVEN_REPOS))
                : getProject().getRepositories().withType(MavenArtifactRepository.class);
        }

        private void apply(Configuration configuration) {
            var hierarchy = configuration.getHierarchy();

            var minecraftDependencies = hierarchy
                .stream()
                .flatMap(c -> c.getDependencies().matching(MinecraftDependencyInternal::is).stream())
                .map(MinecraftDependencyInternal::get)
                .collect(Collectors.toSet());

            for (var minecraftDependency : minecraftDependencies) {
                // This can never be null in production and is only here to make the IDE happy.
                assert minecraftDependency != null;

                minecraftDependency.handle(configuration);
            }
        }

        private void finish(Project project) {
            checkRepos(getRepositories());

            var sourceSetsDir = this.getObjects().directoryProperty().value(this.getProjectLayout().getBuildDirectory().dir("sourceSets"));
            var mergeSourceSets = this.problems.test("net.minecraftforge.gradle.merge-source-sets");
            project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().configureEach(sourceSet -> {
                if (mergeSourceSets) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }

                project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                    var eclipse = project.getExtensions().getByType(EclipseModel.class);
                    if (mergeSourceSets)
                        eclipse.getClasspath().setDefaultOutputDir(sourceSetsDir.getAsFile().get());
                    else
                        problems.reportUnmergedSourceSets();
                });
            });

            if (!this.minecraftDependencies.isEmpty()) {
                var syncMavenizer = project.getTasks().register("syncMavenizer", task -> task.setGroup("Build Setup"));
                for (var minecraftDependency : this.minecraftDependencies) {
                    var mavenizer = minecraftDependency.asTask();
                    if (mavenizer == null) continue;

                    syncMavenizer.configure(task -> task.dependsOn(mavenizer));
                }

                project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                    var eclipse = project.getExtensions().getByType(EclipseModel.class);
                    eclipse.synchronizationTasks(syncMavenizer);
                });

                var taskNames = project.getGradle().getStartParameter().getTaskNames();
                taskNames.add(0, syncMavenizer.get().getPath());
                project.getGradle().getStartParameter().setTaskNames(taskNames);
            }

            if (!this.runs.isEmpty() && !this.minecraftDependencies.isEmpty()) {
                var genEclipseRuns = project.getTasks().register("genEclipseRuns", task -> {
                    task.setGroup("IDE");
                    task.setDescription("Generates the run configuration launch files for Eclipse.");
                });

                File eclipseOutputDir;
                var eclipse = project.getExtensions().findByType(EclipseModel.class);
                if (eclipse != null) {
                    eclipse.synchronizationTasks(genEclipseRuns);
                    eclipseOutputDir = eclipse.getClasspath().getDefaultOutputDir();
                } else {
                    eclipseOutputDir = getProjectLayout().getProjectDirectory().dir("bin").getAsFile();
                }

                var configurations = project.getConfigurations();
                var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();

                sourceSets.configureEach(sourceSet -> {
                    var minecraftDependencies = configurations
                        .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                        .getAllDependencies()
                        .matching(MinecraftDependencyInternal::is)
                        .stream()
                        .map(MinecraftDependencyInternal::get)
                        .collect(Collectors.toSet());

                    boolean single = minecraftDependencies.size() == 1;
                    for (var minecraftDependency : minecraftDependencies) {
                        // This can never be null in production and is only here to make the IDE happy.
                        assert minecraftDependency != null;

                        var impl = (MinecraftDependencyImpl) minecraftDependency;
                        this.runs.forEach(options -> {
                            var task = SlimeLauncherExec.register(project, sourceSet, options, impl.module.get(), impl.version.get(), impl.asPath.get(), impl.asString.get(), single, eclipseOutputDir);
                        });
                    }
                });
            }
        }

        @Override
        public NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns() {
            return this.runs;
        }

        @Override
        public void runs(
            @DelegatesTo(NamedDomainObjectContainer.class)
            @ClosureParams(value = FromString.class, options = "org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>")
            Closure<?> closure
        ) {
            this.runs.configure(closure);
        }

        Class<? extends MinecraftDependencyInternal> getMinecraftDependencyClass() {
            return MinecraftDependencyImpl.class;
        }

        @Override
        public ExternalModuleDependency dependency(
            Object value,
            @DelegatesTo(ExternalModuleDependency.class)
            @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.gradle.MinecraftDependency.ClosureOwner")
            Closure<?> closure
        ) {
            value = Util.unpack(value);

            if (value instanceof ExternalModuleDependencyBundle)
                throw new IllegalArgumentException("Minecraft dependency cannot be a bundle");

            var minecraftDependency = (MinecraftDependencyInternal) this.getObjects().newInstance(this.getMinecraftDependencyClass(), this.mavenizerOutput);
            this.minecraftDependencies.add(minecraftDependency);
            return minecraftDependency.init(value, closure);
        }

        private void checkRepos(List<? extends MavenArtifactRepository> repos) {
            Predicate<String> contains = s -> {
                for (var repo : repos) {
                    if (repo.getUrl().toString().contains(s))
                        return true;
                }

                return false;
            };

            Predicate<Object> containsExactly = object -> {
                for (var repo : repos) {
                    if (repo.getUrl().equals(ForProjectImpl.this.getProject().uri(object)))
                        return true;
                }

                return false;
            };

            // Mavenizer
            if (!containsExactly.test(ForProjectImpl.this.mavenizerOutput.getAsFile())) {
                problems.reportMcMavenNotDeclared();
            }

            // Forge
            if (!contains.test("maven.minecraftforge.net")) {
                problems.reportForgeMavenNotDeclared();
            }

            // Mojang
            if (!contains.test("libraries.minecraft.net")) {
                problems.reportMcLibsMavenNotDeclared();
            }
        }

        static abstract class WithAccessTransformersImpl extends ForProjectImpl<ClosureOwner.MinecraftDependencyWithAccessTransformers> implements MinecraftExtensionInternal.ForProject.WithAccessTransformers {
            private final Property<String> accessTransformers = this.getObjects().property(String.class);

            @Inject
            public WithAccessTransformersImpl(ForgeGradlePlugin plugin) {
                super(plugin);
            }

            @Override
            public TypeOf<?> getPublicType() {
                return MinecraftExtensionInternal.ForProject.WithAccessTransformers.super.getPublicType();
            }

            @Override
            public Property<String> getAccessTransformers() {
                return this.accessTransformers;
            }

            @Override
            final Class<? extends MinecraftDependencyImpl> getMinecraftDependencyClass() {
                return MinecraftDependencyImpl.WithAccessTransformersImpl.class;
            }
        }
    }
}

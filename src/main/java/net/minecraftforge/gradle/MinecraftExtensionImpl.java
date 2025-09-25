/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import com.google.gson.reflect.TypeToken;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.CompileStatic;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import groovy.transform.NamedVariant;
import groovy.transform.PackageScope;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class MinecraftExtensionImpl implements MinecraftExtensionInternal {
    private static final String EXT_MAVEN_REPOS = "fg_mc_maven_repos";
    private static final String EXT_MAPPINGS = "fg_mc_mappings";

    final ForgeGradlePlugin plugin;
    final ForgeGradleProblems problems;

    // MCMaven
    final DirectoryProperty output;

    // Dependencies
    final Property<MinecraftMappings> mappings;

    protected abstract @Inject ObjectFactory getObjects();

    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        var extensions = target.getExtensions();
        if (target instanceof Project project) {
            if (project.getPluginManager().hasPlugin("net.minecraftforge.accesstransformers")) {
                try {
                    extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForProjectImpl.WithAccessTransformersImpl.class, plugin, target);
                } catch (Exception e) {
                    throw project.getObjects().newInstance(ForgeGradleProblems.class).accessTransformersNotOnClasspath(e);
                }
            } else {
                extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForProjectImpl.class, plugin, target);
            }
        } else if (target instanceof Settings) {
            extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.ForSettingsImpl.class, plugin, target);
        } else {
            extensions.create(MinecraftExtension.NAME, MinecraftExtensionImpl.class, plugin);
        }
    }

    @Inject
    public MinecraftExtensionImpl(ForgeGradlePlugin plugin) {
        this.plugin = plugin;
        this.problems = this.getObjects().newInstance(ForgeGradleProblems.class);

        this.output = this.getObjects().directoryProperty().convention(plugin.localCaches().dir("mavenizer/output").map(this.problems.ensureFileLocation()));

        this.mappings = this.getObjects().property(MinecraftMappings.class);
    }

    @Override
    public TypeOf<?> getPublicType() {
        return MinecraftExtensionInternal.super.getPublicType();
    }

    @Override
    public Action<MavenArtifactRepository> getMaven() {
        return maven -> {
            maven.setName("MinecraftMaven");
            maven.setUrl(this.output.getAsFile());
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
        var replacement = new MinecraftMappings(MinecraftMappings.checkParam(this.problems, channel, "channel"), MinecraftMappings.checkParam(this.problems, version, "version"));
        if (this.mappings.isPresent())
            this.problems.reportOverriddenMappings(this.mappings.get(), replacement);

        this.mappings.set(replacement);
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

    @CompileStatic
    @PackageScope
    static abstract class ForSettingsImpl extends MinecraftExtensionImpl {
        public
        @Inject ForSettingsImpl(ForgeGradlePlugin plugin, Settings settings) {
            super(plugin);

            settings.getGradle().settingsEvaluated(it -> {
                if (settings == it)
                    this.finish(it);
            });
        }

        private void finish(Settings settings) {
            var repositories = settings.getDependencyResolutionManagement().getRepositories().withType(MavenArtifactRepository.class);

            settings.getGradle().beforeProject(project -> {
                if (!this.mappings.isPresent()) return;

                var ext = project.getExtensions().getExtraProperties();
                ext.set(EXT_MAVEN_REPOS, repositories);
                ext.set(EXT_MAPPINGS, this.mappings.get());
            });
        }
    }

    static abstract class ForProjectImpl<T extends ClosureOwner<?> & MinecraftDependency & ExternalModuleDependency> extends MinecraftExtensionImpl implements MinecraftExtensionInternal.ForProject<T> {
        private final Project project;

        // Slime Launcher
        private final NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs;
        private final MapProperty<String, RunConfig> configs;

        // Dependencies
        final List<MinecraftDependencyImpl> minecraftDependencies = new ArrayList<>();

        protected abstract @Inject FlowScope getFlowScope();

        protected abstract @Inject FlowProviders getFlowProviders();

        protected abstract @Inject ProjectLayout getProjectLayout();

        protected abstract @Inject ProviderFactory getProviders();

        protected abstract @Inject FileSystemOperations getFileSystemOperations();

        protected abstract @Inject ArchiveOperations getArchiveOperations();

        @Inject
        public ForProjectImpl(ForgeGradlePlugin plugin, Project project) {
            super(plugin);

            this.project = project;

            this.runs = this.getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class);
            this.configs = this.getObjects().mapProperty(String.class, RunConfig.class);

            var ext = project.getExtensions().getExtraProperties();
            if (ext.has(EXT_MAPPINGS))
                this.mappings.set((MinecraftMappings) ext.get(EXT_MAPPINGS));

            //project.extensions.add(DeobfExtension, DeobfExtension.NAME, new DeobfExtensionImpl(project, MinecraftExtensionImpl.this.problems, MinecraftExtensionImpl.this.objects, MinecraftExtensionImpl.this.mappingsProp))

            this.getFlowScope().always(ForgeGradleFlowAction.AccessTransformersMissing.class, spec -> {
                spec.parameters(parameters -> {
                    parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                    parameters.appliedPlugin.set(project.getPluginManager().hasPlugin("net.minecraftforge.accesstransformers"));
                });
            });

            // Finish when the project is evaluated
            project.afterEvaluate(this::finish);
        }

        @Override
        public TypeOf<?> getPublicType() {
            return new TypeOf<MinecraftExtensionForProject<ClosureOwner.MinecraftDependency>>() { };
        }

        @SuppressWarnings("unchecked")
        void finish(Project project) {
            if (this.minecraftDependencies.isEmpty()) {
                this.problems.reportMissingMinecraftDependency();
                return;
            }

            this.minecraftDependencies.forEach(MinecraftDependencyImpl::resolve);

            SyncMinecraftMaven.register(project, this);

            var ext = project.getExtensions().getExtraProperties();
            //noinspection DataFlowIssue
            var appliedRepos = ext.has(EXT_MAVEN_REPOS)
                ? new AppliedRepos((List<? extends MavenArtifactRepository>) ext.get(EXT_MAVEN_REPOS))
                : new AppliedRepos(project.getRepositories().withType(MavenArtifactRepository.class));
            appliedRepos.check();

            var configurations = project.getConfigurations();
            var sourceSets = this.project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();

            var configurationsFiltered = project.getConfigurations().stream().collect(
                Collectors.toMap(Configuration::getName, c -> c.getAllDependencies().stream().map(it -> {
                    for (var minecraftDependency : this.minecraftDependencies) {
                        var dependency = minecraftDependency.getDelegate().get();
                        if (dependency.equals(it))
                            return minecraftDependency;
                    }

                    return null;
                }).filter(Objects::nonNull).toList())
            );

            var sourceSetsDir = this.getObjects().directoryProperty().value(this.getProjectLayout().getBuildDirectory().dir("sourceSets"));
            sourceSets.configureEach(sourceSet -> {
                for (var minecraftDependency : this.minecraftDependencies) {
                    if (Util.contains(configurations, sourceSet, minecraftDependency.getDelegate().get()))
                        minecraftDependency.handle(sourceSet);
                }

                if (this.problems.test("net.minecraftforge.gradle.mergeSourceSets")) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }

                if (!this.runs.isEmpty()) {
                    var minecraftDependencies = configurationsFiltered.get(sourceSet.getRuntimeClasspathConfigurationName());
                    var size = minecraftDependencies != null ? minecraftDependencies.size() : 0;
                    for (var i = 0; i < size; i++) {
                        var dependency = minecraftDependencies.get(0).getDelegate().get();
                        var group = dependency.getGroup();
                        var cacheDir = this.plugin.globalCaches().dir("slime-launcher/cache/%s%s".formatted(group != null ? group.replace('.', '/') + '/' : "", dependency.getVersion())).map(this.problems.ensureFileLocation());
                        var metadataDir = this.getObjects().directoryProperty().value(cacheDir).dir("metadata").map(this.problems.ensureFileLocation());
                        var metadataZip = this.output.file(Util.artifactPath(group, dependency.getName(), dependency.getVersion(), "metadata", "zip"));

                        this.getFileSystemOperations().copy(copy -> copy
                            .from(this.getArchiveOperations().zipTree(metadataZip))
                            .into(metadataDir)
                        );

                        this.configs.set(this.getProviders().provider(() -> {
                            try {
                                return JsonData.fromJson(
                                    metadataDir.get().file("launcher/runs.json").getAsFile(),
                                    new TypeToken<Map<String, RunConfig>>() { }
                                );
                            } catch (Throwable ignored) {
                                // we probably don't have metadata yet. common for fresh setups before first run.
                                // if there's actually a problem, we can throw it in SlimeLauncherExec.
                                return null;
                            }
                        }));

                        this.runs.forEach(options -> SlimeLauncherExec.register(project, sourceSet, options, this.configs.getOrElse(Map.of()), dependency, metadataZip, size == 1));
                    }
                }
            });

            this.getFlowScope().always(ForgeGradleFlowAction.WelcomeMessage.class, spec -> {
                spec.parameters(parameters -> {
                    parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                    parameters.messagesDir.set(this.plugin.globalCaches().dir("messages"));
                    parameters.displayOption.set(
                        this.getProviders().gradleProperty("net.minecraftforge.gradle.messages.welcome")
                            .orElse(this.getProviders().systemProperty("net.minecraftforge.gradle.messages.welcome")).map(
                                it -> ForgeGradleFlowAction.WelcomeMessage.DisplayOption.valueOf(it.toUpperCase(Locale.ROOT))
                            )
                    );
                });
            });
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

        Class<? extends MinecraftDependencyImpl> getMinecraftDependencyClass() {
            return MinecraftDependencyImpl.class;
        }

        @Override
        public Provider<ExternalModuleDependency> dep(
            Object value,
            @DelegatesTo(ExternalModuleDependency.class)
            @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.gradle.MinecraftDependency.ClosureOwner")
            Closure<?> closure
        ) {
            if (value instanceof ExternalModuleDependencyBundle)
                throw new IllegalArgumentException("minecraft.dep does not support bundles");

            var minecraftDependency = (MinecraftDependencyImpl) this.getObjects().newInstance(this.getMinecraftDependencyClass(), this.project);
            this.minecraftDependencies.add(minecraftDependency);
            return minecraftDependency.setDelegate(value, closure);
        }

        private final class AppliedRepos {
            private final List<? extends MavenArtifactRepository> repos;

            private final boolean mcmaven;
            private final boolean forge;
            private final boolean mclibs;

            private AppliedRepos(List<? extends MavenArtifactRepository> repos) {
                this.repos = repos;

                this.mcmaven = containsExactly(ForProjectImpl.this.output.getAsFile());
                this.forge = contains("maven.minecraftforge.net");
                this.mclibs = contains("libraries.minecraft.net");
            }

            private boolean contains(String s) {
                for (var repo : this.repos) {
                    if (repo.getUrl().toString().contains(s))
                        return true;
                }

                return false;
            }

            private boolean containsExactly(Object object) {
                for (var repo : this.repos) {
                    if (repo.getUrl().equals(ForProjectImpl.this.project.uri(object)))
                        return true;
                }

                return false;
            }

            private void check() {
                if (!this.mcmaven)
                    ForProjectImpl.this.problems.reportMcMavenNotDeclared();

                if (!this.forge)
                    ForProjectImpl.this.problems.reportForgeMavenNotDeclared();

                if (!this.mclibs)
                    ForProjectImpl.this.problems.reportMcLibsMavenNotDeclared();
            }
        }

        static abstract class WithAccessTransformersImpl extends ForProjectImpl<ClosureOwner.MinecraftDependencyWithAccessTransformers> implements MinecraftExtensionInternal.ForProject.WithAccessTransformers {
            private final Property<String> accessTransformers = this.getObjects().property(String.class);

            @Inject
            public WithAccessTransformersImpl(ForgeGradlePlugin plugin, Project project) {
                super(plugin, project);
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
            Class<? extends MinecraftDependencyImpl> getMinecraftDependencyClass() {
                return MinecraftDependencyImpl.WithAccessTransformersImpl.class;
            }
        }
    }
}

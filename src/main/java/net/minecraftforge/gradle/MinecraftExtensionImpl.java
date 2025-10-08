/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

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
import net.minecraftforge.util.data.json.RunConfig;
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
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.reflect.TypeOf;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

abstract class MinecraftExtensionImpl implements MinecraftExtensionInternal {
    private static final String EXT_MAVEN_REPOS = "fg_mc_maven_repos";
    private static final String EXT_MAPPINGS = "fg_mc_mappings";

    final ForgeGradleProblems problems;

    // MCMaven
    final DirectoryProperty mavenizerOutput;

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
                    var problems = project.getObjects().newInstance(ForgeGradleProblems.class);
                    throw problems.accessTransformersNotOnClasspath(e);
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
        this.problems = this.getObjects().newInstance(ForgeGradleProblems.class);

        this.mavenizerOutput = this.getObjects().directoryProperty().convention(plugin.localCaches().dir("mavenizer/output").map(this.problems.ensureFileLocation()));

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

            var flowScope = this.getFlowScope();

            flowScope.always(ForgeGradleFlowAction.WelcomeMessage.class, spec -> spec.parameters(parameters -> {
                parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                parameters.messagesDir.set(plugin.globalCaches().dir("messages"));
                parameters.displayOption.set(
                    this.getProviders().gradleProperty("net.minecraftforge.gradle.messages.welcome")
                        .orElse(this.getProviders().systemProperty("net.minecraftforge.gradle.messages.welcome")).map(
                            it -> ForgeGradleFlowAction.WelcomeMessage.DisplayOption.valueOf(it.toUpperCase(Locale.ROOT))
                        )
                );
            }));

            flowScope.always(ForgeGradleFlowAction.AccessTransformersMissing.class, spec -> spec.parameters(parameters -> {
                parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                parameters.appliedPlugin.set(project.getPluginManager().hasPlugin("net.minecraftforge.accesstransformers"));
            }));

            // Finish when the project is evaluated
            project.afterEvaluate(this::finish);
            project.getGradle().projectsEvaluated(gradle -> this.finish(gradle, project));
        }

        @Override
        public TypeOf<?> getPublicType() {
            return new TypeOf<MinecraftExtensionForProject<ClosureOwner.MinecraftDependency>>() { };
        }

        @SuppressWarnings("unchecked")
        private void finish(Project project) {
            var ext = project.getGradle().getExtensions().getExtraProperties();
            var appliedRepos = new AppliedRepos(ext.has(EXT_MAVEN_REPOS)
                ? Objects.requireNonNull((List<? extends MavenArtifactRepository>) ext.get(EXT_MAVEN_REPOS))
                : project.getRepositories().withType(MavenArtifactRepository.class));
            appliedRepos.check();

            var sourceSetsDir = this.getObjects().directoryProperty().value(this.getProjectLayout().getBuildDirectory().dir("sourceSets"));
            project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().configureEach(sourceSet -> {
                if (this.problems.test("net.minecraftforge.gradle.mergeSourceSets")) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }
            });
        }

        private void finish(Gradle gradle, Project project) {
            if (this.minecraftDependencies.isEmpty()) {
                return;
            }

            var configurations = project.getConfigurations();
            var sourceSets = this.project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();

            sourceSets.configureEach(sourceSet -> {
                for (var minecraftDependency : this.minecraftDependencies) {
                    minecraftDependency.resolve();

                    if (Util.contains(configurations, sourceSet, true, minecraftDependency.getDelegate())) {
                        minecraftDependency.handle(sourceSet);
                    }
                }

                if (!this.runs.isEmpty()) {
                    var minecraftDependencies = configurations
                        .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                        .getAllDependencies()
                        .stream()
                        .map(it -> {
                            for (var minecraftDependency : this.minecraftDependencies) {
                                var dependency = minecraftDependency.getDelegate();
                                if (dependency.equals(it))
                                    return minecraftDependency;
                            }

                            return null;
                        })
                        .filter(Objects::nonNull)
                        .toList();

                    boolean single = minecraftDependencies.size() == 1;
                    for (var minecraftDependency : minecraftDependencies) {
                        @SuppressWarnings("DataFlowIssue") // can never be null. this is a bugged warning.
                        var dependency = minecraftDependency.getDelegate();
                        var metadataZip = minecraftDependency.getMetadataZip();

                        this.runs.forEach(options -> SlimeLauncherExec.register(project, sourceSet, options, this.configs.getOrElse(Map.of()), dependency, metadataZip, single));
                    }
                }
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
        public ExternalModuleDependency dependency(
            Object value,
            @DelegatesTo(ExternalModuleDependency.class)
            @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.gradle.MinecraftDependency.ClosureOwner")
            Closure<?> closure
        ) {
            if (value instanceof ExternalModuleDependencyBundle)
                throw new IllegalArgumentException("Minecraft dependency cannot be a bundles");

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

                this.mcmaven = containsExactly(ForProjectImpl.this.mavenizerOutput.getAsFile());
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

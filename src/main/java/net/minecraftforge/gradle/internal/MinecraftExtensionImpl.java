/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.NamedVariant;
import groovy.transform.stc.ClosureParams;
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
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
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
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
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

    protected abstract @Inject ProviderFactory getProviders();

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
        private @Nullable TaskProvider<Task> genEclipseRuns;
        final DirectoryProperty eclipseOutputDir = getObjects().directoryProperty().convention(getProjectLayout().getProjectDirectory().dir("bin"));

        // Slime Launcher
        private final NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs = getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class);

        // Dependencies
        final List<MinecraftDependencyInternal> minecraftDependencies = new ArrayList<>();

        protected abstract @Inject Project getProject();

        protected abstract @Inject FlowScope getFlowScope();

        protected abstract @Inject FlowProviders getFlowProviders();

        protected abstract @Inject ProjectLayout getProjectLayout();

        @Inject
        public ForProjectImpl(ForgeGradlePlugin plugin) {
            super(plugin);

            var ext = getProject().getExtensions().getExtraProperties();
            if (ext.has(EXT_MAPPINGS))
                this.mappings.set((MinecraftMappingsImpl) ext.get(EXT_MAPPINGS));

            plugin.queueMessage(ForgeGradleMessage.WELCOME);
            plugin.queueMessage(ForgeGradleMessage.MAGIC);

            this.getFlowScope().always(ForgeGradleFlowAction.AccessTransformersMissing.class, spec -> spec.parameters(parameters -> {
                parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                parameters.appliedPlugin.set(getProject().getPluginManager().hasPlugin("net.minecraftforge.accesstransformers"));
            }));

            getProject().getConfigurations().configureEach(c -> c.withDependencies(d -> this.apply(c)));

            getProject().getDependencies().attributesSchema(schema -> {
                schema.attribute(ForgeAttributes.OperatingSystem.ATTRIBUTE, strategy -> {
                    var currentOS = getProviders().of(ForgeAttributes.OperatingSystem.CurrentValue.class, ForgeAttributes.OperatingSystem.CurrentValue.Parameters.DEFAULT).get();
                    strategy.getDisambiguationRules().add(ForgeAttributes.OperatingSystem.DisambiguationRule.class, ctor -> ctor.params(currentOS));
                });

                schema.attribute(ForgeAttributes.MappingsChannel.ATTRIBUTE, strategy ->
                    strategy.getDisambiguationRules().add(ForgeAttributes.MappingsChannel.DisambiguationRule.class)
                );

                schema.attribute(ForgeAttributes.MappingsVersion.ATTRIBUTE, strategy ->
                    strategy.ordered(ForgeAttributes.MappingsVersion.COMPARATOR)
                );
            });

            getProject().getPluginManager().withPlugin("eclipse", appliedEclipsePlugin -> {
                var eclipse = getProject().getExtensions().getByType(EclipseModel.class);

                this.genEclipseRuns = getProject().getTasks().register("genEclipseRuns", task -> {
                    task.setGroup("IDE");
                    task.setDescription("Generates the run configuration launch files for Eclipse.");
                });
                eclipse.synchronizationTasks(genEclipseRuns);

                this.eclipseOutputDir.fileProvider(getProviders().provider(() -> eclipse.getClasspath().getDefaultOutputDir()));
            });

            // Finish when the project is evaluated
            getProject().afterEvaluate(this::finish);
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

        @Override
        public DirectoryProperty getEclipseOutputDir() {
            return this.eclipseOutputDir;
        }

        private void apply(Configuration configuration) {
            if (!configuration.isCanBeResolved()) return;

            var hierarchy = configuration.getHierarchy();

            var minecraftDependencies = hierarchy
                .stream()
                .flatMap(c -> c.getDependencies().matching(MinecraftDependencyInternal::is).stream())
                .map(MinecraftDependencyInternal::get)
                .collect(Collectors.toSet());

            for (var minecraftDependency : minecraftDependencies) {
                // This can never be null and is only here to make the IDE happy.
                assert minecraftDependency != null;

                minecraftDependency.handle(configuration);
            }
        }

        private void finish(Project project) {
            checkRepos(getRepositories());

            var sourceSets = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
            var sourceSetsDir = this.getObjects().directoryProperty().value(this.getProjectLayout().getBuildDirectory().dir("sourceSets"));
            var mergeSourceSets = this.problems.test("net.minecraftforge.gradle.merge-source-sets");
            sourceSets.all(sourceSet -> {
                var sourceSetName = sourceSet.getName();
                var syncMavenizer = Util.runFirst(project, project.getTasks().register(sourceSet.getTaskName("sync", "mavenizer"), task -> {
                    task.setGroup("Build Setup");
                    task.setDescription("Synchronizes the Mavenizer output for source set '" + sourceSetName + '.');
                }));

                try {
                    project.getTasks().named(sourceSet.getCompileJavaTaskName(), task -> task.dependsOn(syncMavenizer));
                } catch (UnknownTaskException ignored) { }

                if (mergeSourceSets) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }

                project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                    var eclipse = project.getExtensions().getByType(EclipseModel.class);
                    eclipse.synchronizationTasks(syncMavenizer);

                    if (mergeSourceSets)
                        eclipse.getClasspath().setDefaultOutputDir(sourceSetsDir.getAsFile().get());
                    else
                        problems.reportUnmergedSourceSets();
                });
            });

            for (var minecraftDependency : this.minecraftDependencies) {
                var dependency = minecraftDependency.asDependency();
                if (dependency != null) {
                    minecraftDependency.handle(
                        Util.collect(project, false, dependency),
                        Util.collect(project, true, dependency)
                    );

                    // See https://github.com/MinecraftForge/ForgeGradle/issues/1008#issuecomment-3623727384
                    // Basically, if IntelliJ can't immediately find a sources JAR next to the main jar, it tries to use this scuffed task
                    // Intercept the configuration that the task uses and replace any dependencies we have with our own
                    project.getConfigurations().named(name -> name.startsWith("downloadArtifact_")).configureEach(configuration -> {
                        var itor = configuration.getDependencies().iterator();
                        while (itor.hasNext()) {
                            var existing = itor.next();
                            if (!Objects.equals(dependency.getGroup(), existing.getGroup())
                                || !dependency.getName().equals(existing.getName())
                                || !Objects.equals(dependency.getVersion(), existing.getVersion()))
                                continue;

                            itor.remove();
                            var replacement = dependency.copy();
                            replacement.attributes(a -> {
                                a.attribute(Category.CATEGORY_ATTRIBUTE, a.named(Category.class, Category.DOCUMENTATION));
                                a.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, a.named(DocsType.class, DocsType.SOURCES));
                            });
                            configuration.withDependencies(dependencies ->
                                dependencies.add(replacement)
                            );
                        }
                    });
                }
            }
        }

        @Override
        public NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns() {
            return this.runs;
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

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
import net.minecraftforge.gradle.MavenizerInstance;
import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.initialization.Settings;
import org.gradle.api.initialization.resolve.DependencyResolutionManagement;
import org.gradle.api.initialization.resolve.RepositoriesMode;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

abstract class MinecraftExtensionImpl implements MinecraftExtensionInternal {
    private static final String MAVENIZER_REPO_NAME = "MinecraftMavenizer";

    private final DirectoryProperty mavenizerOutput = getObjects().directoryProperty();

    private final Property<MinecraftMappingsInternal> mappings = getObjects().property(MinecraftMappingsInternal.class);

    private final ForgeGradleProblems problems = getObjects().newInstance(ForgeGradleProblems.class);

    protected final ForgeGradlePlugin plugin;

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    // TODO [ForgeGradle] KnownPlugins system. See https://github.com/LexManos/ForgeGradle/commit/7c59cf3c8d54a89e01cd14a0c5ab75ea51918360
    //      Specifically, the ability to hide external plugin types from method signatures and lambdas using a dummy MissingExtension
    //      Thankfully, this will very likely not affect the base DSL at all, making it only additive.
    //      But for now, MinecraftExtensionForProject and MinecraftDependency both implement MinecraftAccessTransformersContainer
    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        var extensions = target.getExtensions();
        if (target instanceof Project) {
            extensions.create(MinecraftExtensionForProject.class, MinecraftExtension.NAME, ForProjectImpl.class, plugin);
        } else if (target instanceof Settings) {
            extensions.create(MinecraftExtension.class, MinecraftExtension.NAME, ForSettingsImpl.class, plugin, target);
        } else {
            extensions.create(MinecraftExtension.class, MinecraftExtension.NAME, MinecraftExtensionImpl.class, plugin);
        }
    }

    @Inject
    public MinecraftExtensionImpl(ForgeGradlePlugin plugin) {
        this.plugin = plugin;
        this.mavenizerOutput.convention(plugin.rootProjectDirectory().dir(".gradle/mavenizer/repo").map(this.problems.ensureFileLocation()));
        this.mappings.convention(getObjects().newInstance(MinecraftMappingsImpl.class, "auto", ""));
    }

    @Override
    public TypeOf<?> getPublicType() {
        return MinecraftExtensionInternal.super.getPublicType();
    }

    @Override
    public Property<MinecraftMappingsInternal> getMappingsProperty() {
        return this.mappings;
    }

    @Override
    public DirectoryProperty getMavenizerOutput() {
        return this.mavenizerOutput;
    }

    @Override
    public Action<MavenArtifactRepository> getMavenizer() {
        return maven -> {
            maven.setName(MAVENIZER_REPO_NAME);
            maven.setUrl(this.getMavenizerOutput());
        };
    }

    @Override
    public MinecraftMappings getMappings() {
        try {
            return this.mappings.get();
        } catch (IllegalStateException e) {
            throw problems.missingMappings(e);
        }
    }

    @Override
    @NamedVariant
    public void mappings(String channel, String version) {
        var replacement = getObjects().newInstance(MinecraftMappingsImpl.class, channel, version);
        if (this.mappings.isPresent())
            problems.reportOverriddenMappings(this.mappings.get(), replacement);

        this.mappings.set(replacement);
    }

    // TODO [ForgeGradle] Do this better (most likely by getting a list of possible artifacts from Mavenizer)
    static void applyComponentRules(ComponentMetadataHandler components) {
        components.all(ForgeGradleComponentMetadataRules.AlwaysUseMatureStatus.class, ctor ->
            ctor.params(List.of("net.minecraftforge:forge", "net.minecraftforge:fmlonly"))
        );
    }

    static abstract class ForSettingsImpl extends MinecraftExtensionImpl {
        @Inject
        public ForSettingsImpl(ForgeGradlePlugin plugin, Settings settings) {
            super(plugin);

            // Add component rules, even if they aren't used
            // RulesMode.PREFER_PROJECT && !projectRules.isEmpty() -> use projectRules
            applyComponentRules(settings.getDependencyResolutionManagement().getComponents());

            // Add shared data to each project before it is evaluated
            var sharedData = new ForgeGradleSharedData(
                this.getMappingsProperty().getOrNull()
            );
            settings.getGradle().getLifecycle().beforeProject(project -> beforeProject(project, sharedData));
        }

        // This has to be static for serialization / isolation purposes
        private static void beforeProject(Project project, ForgeGradleSharedData sharedData) {
            // Attach shared data to Gradle instance (accessible to project)
            project.getExtensions().add(
                ForgeGradleSharedData.NAME,
                sharedData
            );
        }
    }

    static abstract class ForProjectImpl extends MinecraftExtensionImpl implements ForProject {
        private final TaskProvider<Task> genEclipseRuns;

        // Slime Launcher
        private final NamedDomainObjectContainer<SlimeLauncherOptionsImpl> runs = getObjects().domainObjectContainer(SlimeLauncherOptionsImpl.class);

        // Dependencies
        private final List<MinecraftDependencyInternal> minecraftDependencies = new ArrayList<>();
        private final Map<String, MavenizerInstanceImpl> mavenizerRegistry = new HashMap<>();

        // Access Transformers
        private final boolean hasAccessTransformersPlugin;
        private final ConfigurableFileCollection accessTransformer = getObjects().fileCollection();
        private final Property<String> accessTransformerPath = getObjects().property(String.class);

        // Facades
        private final ConfigurableFileCollection facades = getObjects().fileCollection();
        // Extra Mavenizer Arguments
        private final ListProperty<String> extraMavenizerArguments = this.getObjects().listProperty(String.class);

        private final ForgeGradleProblems problems = getObjects().newInstance(ForgeGradleProblems.class);

        protected abstract @Inject Project getProject();

        protected abstract @Inject DependencyResolutionManagement getDependencyResolutionManagement();

        protected abstract @Inject FlowScope getFlowScope();

        protected abstract @Inject FlowProviders getFlowProviders();

        protected abstract @Inject ProjectLayout getProjectLayout();

        @Inject
        public ForProjectImpl(ForgeGradlePlugin plugin) {
            super(plugin);

            if (getProject().getPluginManager().hasPlugin("net.minecraftforge.accesstransformers")) {
                hasAccessTransformersPlugin = true;
            } else {
                hasAccessTransformersPlugin = false;
                getProject().getPluginManager().withPlugin("net.minecraftforge.accesstransformers", appliedPlugin -> {
                    // TODO Report AccessTransformers applied in wrong order
                });
            }

            var sharedData = getProject().getExtensions().findByType(ForgeGradleSharedData.class);
            if (sharedData != null) {
                this.getMappingsProperty().value(sharedData.mappings());
            }

            plugin.queueMessage(ForgeGradleMessage.WELCOME);
            //plugin.queueMessage(ForgeGradleMessage.MAGIC);

            getFlowScope().always(ForgeGradleFlowAction.AccessTransformersMissing.class, spec -> spec.parameters(parameters -> {
                parameters.getFailure().set(getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                parameters.appliedPlugin.set(hasAccessTransformersPlugin);
            }));

            getProject().getConfigurations().configureEach(c -> c.withDependencies(d -> this.apply(c)));

            // Dependencies
            {
                var dependencies = getProject().getDependencies();

                try {
                    applyComponentRules(dependencies.getComponents());
                } catch (Exception ignored) { }

                var attributesSchema = dependencies.getAttributesSchema();
                attributesSchema.attribute(ForgeAttributes.OperatingSystem.ATTRIBUTE, strategy -> {
                    var currentOS = getProviders().of(ForgeAttributes.OperatingSystem.CurrentValue.class, ForgeAttributes.OperatingSystem.CurrentValue.Parameters.DEFAULT).get();
                    strategy.getDisambiguationRules().add(ForgeAttributes.OperatingSystem.DisambiguationRule.class, ctor -> ctor.params(currentOS));
                });
                attributesSchema.attribute(ForgeAttributes.MappingsChannel.ATTRIBUTE, strategy ->
                    strategy.getDisambiguationRules().add(ForgeAttributes.MappingsChannel.DisambiguationRule.class)
                );
                attributesSchema.attribute(ForgeAttributes.MappingsVersion.ATTRIBUTE, strategy ->
                    strategy.ordered(ForgeAttributes.MappingsVersion.COMPARATOR)
                );
            }

            genEclipseRuns = getProject().getTasks().register("genEclipseRuns", task -> {
                task.setGroup("IDE");
                task.setDescription("Generates the run configuration launch files for Eclipse.");
            });
            getProject().getPluginManager().withPlugin("eclipse", appliedPlugin ->
                getProject().getExtensions().configure(EclipseModel.class, eclipse -> {
                    eclipse.synchronizationTasks(genEclipseRuns);
                })
            );

            // Finish when the project is evaluated
            getProject().afterEvaluate(this::finish);
        }

        @Override
        public TypeOf<?> getPublicType() {
            return ForProject.super.getPublicType();
        }

        @Override
        public boolean hasAccessTransformersPlugin() {
            return this.hasAccessTransformersPlugin;
        }

        @Override
        public ConfigurableFileCollection getAccessTransformer() {
            return this.accessTransformer;
        }

        @Override
        public Property<String> getAccessTransformerPath() {
            return this.accessTransformerPath;
        }

        @Override
        public ConfigurableFileCollection getFacade() {
            return this.facades;
        }

        @Override
        public ListProperty<String> getMavenizerArguments() {
            return this.extraMavenizerArguments;
        }

        @Override
        public List<? extends MavenArtifactRepository> getRepositories() {
            var repositoriesMode = getDependencyResolutionManagement().getRepositoriesMode().getOrElse(RepositoriesMode.PREFER_PROJECT);
            var projectRepositories = getProject().getRepositories().withType(MavenArtifactRepository.class);
            var settingsRepositories = getDependencyResolutionManagement().getRepositories().withType(MavenArtifactRepository.class);

            return switch (repositoriesMode) {
                case FAIL_ON_PROJECT_REPOS -> Collections.unmodifiableList(settingsRepositories);
                case PREFER_SETTINGS ->
                    Collections.unmodifiableList(!projectRepositories.isEmpty() && settingsRepositories.isEmpty() ? projectRepositories : settingsRepositories);
                case PREFER_PROJECT ->
                    Collections.unmodifiableList(!settingsRepositories.isEmpty() && projectRepositories.isEmpty() ? settingsRepositories : projectRepositories);
            };
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
                if (mergeSourceSets) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.getName());
                    sourceSet.getOutput().setResourcesDir(unifiedDir);
                    sourceSet.getJava().getDestinationDirectory().set(unifiedDir);
                }
            });

            project.getPluginManager().withPlugin("eclipse", eclipsePlugin -> {
                if (mergeSourceSets)
                    project.getExtensions().configure(EclipseModel.class, eclipse -> eclipse.classpath(classpath -> {
                        var output = sourceSetsDir.getAsFile().get();
                        classpath.setDefaultOutputDir(output);
                        classpath.getBaseSourceOutputDir().set(output);
                    }));
                else
                    problems.reportUnmergedSourceSets();
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

        @SuppressWarnings({"UnstableApiUsage"})
        @Override
        public MavenizerInstance dependency(
            String name,
            Object value,
            @DelegatesTo(ExternalModuleDependency.class)
            @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.gradle.MinecraftDependency.ClosureOwner")
            Closure<?> closure
        ) {
            value = Util.unpack(value);

            if (value instanceof ExternalModuleDependencyBundle)
                throw new IllegalArgumentException("Minecraft dependency cannot be a bundle");

            var minecraftDependency = this.getObjects().newInstance(MinecraftDependencyImpl.class, name);
            this.minecraftDependencies.add(minecraftDependency);
            var dep = minecraftDependency.init(value, closure);
            var outputJson = this.plugin.rootProjectDirectory().file(".gradle/mavenizer/dependencies/" + name + ".json").get().getAsFile();

            var mavenizer = this.getProviders().of(MavenizerValueSource.class, spec -> {
                spec.parameters(params -> {
                    var tool = this.plugin.getTool(Tools.MAVENIZER);
                    var toolVersion = new ComparableVersion(tool.getModule().getVersion());
                    params.getClasspath().setFrom(tool.getClasspath());
                    params.getJavaLauncher().set(tool.getJavaLauncher().map(JavaLauncher::getExecutablePath));
                    params.getArguments().set(this.getProviders().provider(() -> {
                        var toolCache = this.plugin.globalCaches()
                            .dir(tool.getName().toLowerCase(Locale.ENGLISH))
                            .map(this.problems.ensureFileLocation());
                        var cache = toolCache.get().dir("caches").getAsFile().getAbsolutePath();

                        var localToolCache = this.plugin.localCaches()
                            .dir(tool.getName().toLowerCase(Locale.ENGLISH))
                            .map(this.problems.ensureFileLocation());

                        var ret = new ArrayList<>(List.of(
                            "--maven",
                            "--cache", cache,
                            "--jdk-cache", cache,
                            "--output", this.getMavenizerOutput().get().getAsFile().getAbsolutePath(),
                            "--artifact", dep.getModule().toString(),
                            "--version", Objects.requireNonNull(dep.getVersion()),
                            "--global-auxiliary-variants",
                            "--output-json", outputJson.getAbsolutePath()
                        ));

                        if (getProject().getGradle().getStartParameter().isRefreshDependencies())
                            ret.add("--ignore-cache");

                        // If we are finding the access transformer from sourcesets, just find from any source set
                        // We can't filter by configurations because the config cache doesn't like that.
                        // So if users fuck up, then we can output a warning, or they can manually set the AT file.
                        // This is a 'best effort'
                        var sourceSets = getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets();
                        minecraftDependency.finalizeAccessTransformers(sourceSets);

                        for (var at : minecraftDependency.getAccessTransformer()) {
                            var path = at.getAbsolutePath();
                            //System.out.println("Access Transformer: " + at);
                            if (path.endsWith(".zip") || path.endsWith(".jar"))
                                this.problems.reportInvalidAccessTransformerConfig(path);
                            else {
                                ret.add("--access-transformer");
                                ret.add(at.getAbsolutePath());
                            }
                        }

                        var mappings = minecraftDependency.getMappings();
                        if ("parchment".equals(mappings.getChannel()))
                            ret.addAll(List.of("--parchment", Objects.requireNonNull(mappings.getVersion())));
                        else if (!"auto".equals(mappings.getChannel())) {
                            ret.add("--mappings");
                            if (mappings.getVersion() == null)
                                ret.add(mappings.getChannel());
                            else
                                ret.add(mappings.getChannel() + ':' + mappings.getVersion());
                        }

                        for (var repo : this.getRepositories()) {
                            if (MAVENIZER_REPO_NAME.equals(repo.getName()))
                                continue;
                            var url = repo.getUrl().toString();
                            if (!url.endsWith("/"))
                                url += '/';
                            ret.add("--repository");
                            ret.add(repo.getName() + ',' + url);
                        }

                        if (!minecraftDependency.getFacade().isEmpty()) {
                            if (toolVersion.compareTo(Constants.Mavenizer.SUPPORTS_FACADES) < 0) {
                                problems.reportFacadesNotSupported(tool.getModule().toString());
                            } else {
                                for (var cfg : minecraftDependency.getFacade()) {
                                    var path = cfg.getAbsolutePath();
                                    if (path.endsWith(".zip") || path.endsWith(".jar"))
                                        this.problems.reportInvalidFacadeConfig(path);
                                    else {
                                        ret.add("--facade-config");
                                        ret.add(cfg.getAbsolutePath());
                                    }
                                }
                            }
                        }

                        if (toolVersion.compareTo(Constants.Mavenizer.SUPPORTS_LOCAL_CACHE) >= 0) {
                            ret.add("--local-cache");
                            ret.add(localToolCache.get().getAsFile().getAbsolutePath());
                        }

                        // Make sure to do this at the very end
                        ret.addAll(minecraftDependency.getMavenizerArguments().getOrElse(Collections.emptyList()));

                        return ret;
                    }));
                });
            });

            var instance = new MavenizerInstanceImpl(this, mavenizer, dep, outputJson);
            if (this.mavenizerRegistry.put(name, instance) != null)
                problems.reportDuplicateMavenizerNames(name);
            return instance;
        }

        @Override
        public MavenizerInstance getDependency(String name) {
            var ret = this.mavenizerRegistry.get(name);
            if (ret == null)
                throw problems.mavenizerInstanceNotFound(new NoSuchElementException("Mavenizer instance not found: " + name), name);
            return ret;
        }

        @Override
        public Collection<MavenizerInstanceImpl> getDependencies() {
            return Collections.unmodifiableCollection(this.mavenizerRegistry.values());
        }

        private void checkRepos(List<? extends MavenArtifactRepository> repos) {
            ToIntFunction<String> indexOf = s -> {
                for (var i = 0; i < repos.size(); i++) {
                    var repo = repos.get(i);
                    if (repo.getUrl().toString().contains(s))
                        return i;
                }

                return -1;
            };

            ToIntFunction<Object> indexOfExactly = object -> {
                for (var i = 0; i < repos.size(); i++) {
                    var repo = repos.get(i);
                    if (repo.getUrl().equals(getProject().uri(object)))
                        return i;
                }

                return -1;
            };

            // Mavenizer
            int mavenizer = indexOfExactly.applyAsInt(ForProjectImpl.this.getMavenizerOutput().getAsFile());
            if (mavenizer < 0)
                problems.reportMcMavenNotDeclared();
            else if (mavenizer != 0) // if Mavenizer is not highest repository
                problems.reportMavenizerNotHighestRepository();

            // Forge
            int forge = indexOf.applyAsInt("maven.minecraftforge.net");
            if (forge < 0) {
                problems.reportForgeMavenNotDeclared();
            } else if (mavenizer > 0 && forge < mavenizer) // If Forge is above Mavenizer
                problems.reportForgeAboveMavenizer();

            // Mojang
            int mojang = indexOf.applyAsInt("libraries.minecraft.net");
            if (mojang < 0)
                problems.reportMcLibsMavenNotDeclared();
        }
    }
}

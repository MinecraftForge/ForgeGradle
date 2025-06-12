/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle

import com.google.gson.reflect.TypeToken
import groovy.transform.CompileStatic
import groovy.transform.NamedVariant
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer
import net.minecraftforge.util.data.json.JsonData
import net.minecraftforge.util.data.json.RunConfig
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.PluginAware
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.reflect.TypeOf
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.OperatingSystemFamily

import java.util.concurrent.Callable
import java.util.function.BiFunction

@CompileStatic
@PackageScope final class MinecraftExtensionImpl implements MinecraftExtension {
    private static final String EXT_MAVEN_REPOS = 'fg_mc_maven_repos'
    private static final String EXT_MAPPINGS = 'fg_mc_mappings'

    private final ForgeGradlePlugin plugin
    private final ObjectFactory objects

    // MCMaven
    private final DirectoryProperty output

    // Dependencies
    private final Property<Mappings> mappingsProp

    @PackageScope static void register(
        ExtensionAware target,
        ForgeGradlePlugin plugin,
        Callable<? extends FlowScope> flowScope,
        Callable<? extends FlowProviders> flowProviders,
        Callable<? extends ObjectFactory> objects,
        Callable<? extends ProjectLayout> layout,
        Callable<? extends ProviderFactory> providers,
        Callable<? extends FileSystemOperations> fileSystemOperations,
        Callable<? extends ArchiveOperations> archiveOperations
    ) {
        final minecraft = new MinecraftExtensionImpl(plugin, objects.call())

        if (target instanceof Project) {
            target.extensions.add(MinecraftExtension.ForProject, MinecraftExtension.NAME, minecraft.forProject(target, flowScope.call(), flowProviders.call(), layout.call(), providers.call(), fileSystemOperations.call(), archiveOperations.call()))
        } else if (target instanceof Settings) {
            target.extensions.add(MinecraftExtension, MinecraftExtension.NAME, minecraft.forSettings(target))
        } else {
            target.extensions.add(MinecraftExtension, MinecraftExtension.NAME, minecraft)
        }
    }

    private MinecraftExtensionImpl(ForgeGradlePlugin plugin, ObjectFactory objects) {
        this.plugin = plugin
        this.objects = objects

        this.output = objects.directoryProperty().convention(plugin.globalCaches.dir('mavenizer/output').map(problems.ensureDirectory()))

        this.mappingsProp = objects.property(Mappings)
    }

    @PackageScope ForgeGradleProblems getProblems() {
        this.plugin.enhancedProblems
    }

    private MinecraftExtension forSettings(Settings target) {
        target.gradle.settingsEvaluated { Settings settings ->
            if (settings !== target) return

            var repositories = settings.dependencyResolutionManagement.repositories.withType(MavenArtifactRepository)

            final path = settings.rootProject.path
            settings.gradle.beforeProject { Project project ->
                if (project.path != path || !this.mappingsProp.present) return

                project.allprojects { p ->
                    p.extensions.extraProperties.tap {
                        set EXT_MAVEN_REPOS, repositories
                        set EXT_MAPPINGS, this.mappings
                    }
                }
            }
        }

        return this
    }

    private MinecraftExtension.ForProject forProject(Project project, FlowScope flowScope, FlowProviders flowProviders, ProjectLayout layout, ProviderFactory providers, FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations) {
        new ForProjectImpl(project, flowScope, flowProviders, layout, providers, fileSystemOperations, archiveOperations)
    }

    @Override
    Closure getMaven() {
        { MavenArtifactRepository maven ->
            maven.name = 'MinecraftMaven'
            maven.url = this.output.asFile
        }
    }

    @Override
    MinecraftExtension.Mappings getMappings() {
        try {
            this.mappingsProp.get()
        } catch (IllegalStateException e) {
            throw this.problems.missingMappings(e)
        }
    }

    @Override
    @NamedVariant
    void mappings(String channel, String version) {
        // manual null-checks here instead of @NullCheck for enhanced problems reporting
        Mappings.checkParam(this.problems, channel, 'channel')
        Mappings.checkParam(this.problems, version, 'version')

        final replacement = new Mappings(channel, version)
        if (this.mappingsProp.present)
            this.problems.reportOverriddenMappings(this.mappingsProp.get(), replacement)

        this.mappingsProp.set replacement
    }

    @CompileStatic
    @PackageScope final class ForProjectImpl implements MinecraftExtension.ForProject {
        private final Project project
        private final ProjectLayout layout
        private final ProviderFactory providers

        // Caches
        private final DirectoryProperty localCaches

        // Slime Launcher
        final NamedDomainObjectContainer<SlimeLauncherOptions> runs
        private final MapProperty<String, RunConfig> configs

        private final Util.ActionableLazy<AccessTransformersContainer> atContainer = Util.lazy {
            this.project.pluginManager.apply('net.minecraftforge.accesstransformers')
            AccessTransformersContainer.register(this.project, Attribute.of('net.minecraftforge.gradle.accesstransformed', Boolean)) { }
        }

        // Dependencies
        private List<MinecraftDependencyImpl> minecraftDependencies = new ArrayList<>()

        private ForProjectImpl(Project project, FlowScope flowScope, FlowProviders flowProviders, ProjectLayout layout, ProviderFactory providers, FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations) {
            this.project = project
            this.layout = layout
            this.providers = providers

            this.localCaches = MinecraftExtensionImpl.this.objects.directoryProperty().convention(
                this.layout.buildDirectory.dir(Constants.CACHES_LOCATION).map(MinecraftExtensionImpl.this.problems.ensureDirectory())
            )

            this.runs = SlimeLauncherOptions.container(MinecraftExtensionImpl.this.objects, layout)
            this.configs = MinecraftExtensionImpl.this.objects.mapProperty(String, RunConfig)

            if (project.extensions.extraProperties.has(EXT_MAPPINGS))
                MinecraftExtensionImpl.this.mappingsProp.set project.extensions.extraProperties.get(EXT_MAPPINGS) as Mappings

            project.extensions.add(DeobfExtension, DeobfExtension.NAME, new DeobfExtensionImpl(project, MinecraftExtensionImpl.this.problems, MinecraftExtensionImpl.this.objects, MinecraftExtensionImpl.this.mappingsProp))

            // Finish when the project is evaluated
            project.afterEvaluate { this.finish(it, flowScope, flowProviders, fileSystemOperations, archiveOperations) }
        }

        private void applyAttributes(AttributeContainer a) {
            a.attribute(Attributes.os, objects.named(OperatingSystemFamily, OperatingSystem.current().familyName))
            a.attribute(Attributes.mappingsChannel, MinecraftExtensionImpl.this.mappings.channel())
            a.attribute(Attributes.mappingsVersion, MinecraftExtensionImpl.this.mappings.version())
        }

        private void finish(Project project, FlowScope flowScope, FlowProviders flowProviders, FileSystemOperations fileSystemOperations, ArchiveOperations archiveOperations) {
            if (this.minecraftDependencies.isEmpty()) {
                MinecraftExtensionImpl.this.problems.reportMissingMinecraftDependency()
                return
            } else {
                if (this.atContainer.present && this.minecraftDependencies.size() > 1) {
                    throw new UnsupportedOperationException('Cannot use the global minecraft.accessTransformers object with more than one Minecraft dependency')
                }

                this.minecraftDependencies.forEach {
                    it.finish(this.&getMappings, this.atContainer)
                }
            }

            project.configurations.configureEach {
                if (it.canBeResolved)
                    it.attributes(this.&applyAttributes)
            }

            SyncMinecraftMaven.register(project, this.minecraftDependencies)

            var repositories = project.extensions.extraProperties.has(EXT_MAVEN_REPOS)
                ? new AppliedRepos(project.extensions.extraProperties.get(EXT_MAVEN_REPOS) as List<? extends MavenArtifactRepository>)
                : new AppliedRepos(project.repositories.withType(MavenArtifactRepository))

            if (!repositories.mcmaven)
                MinecraftExtensionImpl.this.problems.reportMcMavenNotDeclared()

            if (!repositories.forge)
                MinecraftExtensionImpl.this.problems.reportForgeMavenNotDeclared()

            if (!repositories.mclibs)
                MinecraftExtensionImpl.this.problems.reportMcLibsMavenNotDeclared()

            var sourceSetsDir = objects.directoryProperty().value(this.layout.buildDirectory.dir('sourceSets'))
            this.project.getExtensions().getByType(JavaPluginExtension).sourceSets.configureEach { sourceSet ->
                if (!Util.isFalse(this.providers, 'net.minecraftforge.gradle.mergeSourceSets')) {
                    // This is documented in SourceSetOutput's javadoc comment
                    var unifiedDir = sourceSetsDir.dir(sourceSet.name)
                    sourceSet.output.resourcesDir = unifiedDir
                    sourceSet.java.destinationDirectory.set unifiedDir
                }

                if (!this.runs.empty) {
                    var allDependencies = this.project.configurations.findByName(sourceSet.runtimeClasspathConfigurationName)?.allDependencies?.findAll { this.minecraftDependencies.contains(it) }

                    if (allDependencies === null || allDependencies.empty) {
                        throw new IllegalArgumentException('Cannot create run configurations without any Minecraft dependencies')
                    } else if (allDependencies.size() > 1) {
                        throw new IllegalArgumentException('Cannot create run configurations for more than one Minecraft dependency')
                    } else {
                        var cacheDir = MinecraftExtensionImpl.this.plugin.globalCaches.dir("slime-launcher/cache/${this.minecraftDependencies[0].group.replace('.', '/')}/${this.minecraftDependencies[0].name}/${this.minecraftDependencies[0].version}").map(MinecraftExtensionImpl.this.problems.ensureDirectory())
                        var metadataDir = MinecraftExtensionImpl.this.objects.directoryProperty().value(cacheDir).dir('metadata').map(MinecraftExtensionImpl.this.problems.ensureDirectory())
                        var metadataZip = MinecraftExtensionImpl.this.output.file(Util.artifactPath(this.minecraftDependencies[0].group, this.minecraftDependencies[0].name, this.minecraftDependencies[0].version, 'metadata', 'zip'))

                        try {
                            fileSystemOperations.copy(copy -> copy
                                .from(archiveOperations.zipTree(metadataZip))
                                .into(metadataDir)
                            )

                            this.configs.set JsonData.fromJson(
                                metadataDir.get().file('launcher/runs.json').asFile,
                                new TypeToken<Map<String, RunConfig>>() {}
                            )
                        } catch (Throwable ignored) {
                            // we probably don't have metadata yet. common for fresh setups before first run.
                            // if there's actually a problem, we can throw it in SlimeLauncherExec.
                        }

                        this.runs.forEach { options ->
                            SlimeLauncherExec.register(project, sourceSet, options, this.configs.getOrElse(Map.of()), this.minecraftDependencies[0], metadataZip)
                        }
                    }
                }
            }

            flowScope.always(ForgeGradleFlowAction.WelcomeMessage) {
                it.parameters {
                    it.failure.set flowProviders.buildWorkResult.map { it.failure.orElse(null) }
                    it.messagesDir.set MinecraftExtensionImpl.this.plugin.globalCaches.dir('messages')
                    it.displayOption.set this.providers.gradleProperty('net.minecraftforge.gradle.messages.welcome').map {
                        ForgeGradleFlowAction.WelcomeMessage.DisplayOption.valueOf(it.toUpperCase(Locale.ROOT))
                    }
                }
            }
        }

        @Override
        void setAccessTransformer(Provider<?> configFile) {
            this.accessTransformer { config = configFile }
        }

        @Override
        void setAccessTransformer(RegularFile configFile) {
            this.accessTransformer { config = configFile }
        }

        @Override
        void setAccessTransformer(File configFile) {
            this.accessTransformer { config = configFile }
        }

        @Override
        void setAccessTransformer(Object configFile) {
            this.accessTransformer { config = configFile }
        }

        @Override
        void accessTransformer(
            @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
                Closure options
        ) {
            this.atContainer.map { it.options options }
        }

        @Override
        void runs(
            @DelegatesTo(NamedDomainObjectContainer)
            @ClosureParams(value = SimpleType, options = 'org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>')
                Closure<Void> closure
        ) {
            this.runs.configure(closure)
        }

        @Override
        MinecraftDependency dep(
            def value,
            @DelegatesTo(value = ExternalModuleDependency, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType, options = 'net.minecraftforge.gradle.MinecraftDependency')
                Closure closure
        ) {
            // creation + validation
            new MinecraftDependencyImpl(this.project.dependencies.create(value), this.project, MinecraftExtensionImpl.this.problems, MinecraftExtensionImpl.this.objects, this.providers).tap { dependency ->
                // configuration
                Closures.invoke(dependency, closure)

                // finish
                this.minecraftDependencies.add(dependency)
            }
        }

        private final class AppliedRepos {
            private final boolean mcmaven
            private final boolean forge
            private final boolean mclibs

            private AppliedRepos(List<? extends MavenArtifactRepository> repos) {
                final contains = { String s ->
                    repos.find { repo ->
                        repo instanceof MavenArtifactRepository && repo.url.toString().contains(s)
                    } !== null
                }

                final containsExactly = { def object ->
                    repos.find { repo ->
                        repo instanceof MavenArtifactRepository && repo.url == ForProjectImpl.this.project.uri(object)
                    } !== null
                }

                this.mcmaven = containsExactly output.asFile
                this.forge = contains 'maven.minecraftforge.net'
                this.mclibs = contains 'libraries.minecraft.net'
            }
        }


        /* IMPLEMENTED UPPER METHODS */

        @Override
        Closure getMaven() {
            MinecraftExtensionImpl.this.maven
        }

        @Override
        Mappings getMappings() {
            MinecraftExtensionImpl.this.mappings
        }

        @Override
        void mappings(String channel, String version) {
            MinecraftExtensionImpl.this.mappings channel, version
        }

        @Override
        void mappings(Map namedArgs) {
            MinecraftExtensionImpl.this.mappings namedArgs
        }
    }

    // NOTE: Cannot be annotated with @Singleton as the instance references are bugged with Java classes
    @CompileStatic
    @PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.CONSTRUCTORS])
    static final class AttributesImpl implements MinecraftExtension.Attributes {}
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle


import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import net.minecraftforge.util.data.json.RunConfig
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.problems.Problems
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin

import javax.inject.Inject
import java.nio.file.Files

/**
 * This task executes Slime Launcher, Forge's dedicated slim launcher for Minecraft in the development environment. It
 * alone is responsible for all the tasks revolving around running the game with the exception of JVM forking arguments
 * which are handled by ForgeGradle.
 * <p>Slime Launcher is added to the applied project through the minecraft extension to the {@code runtimeOnly}
 * configuration, so that the project's entire classpath can be used when starting the game.
 */
@CompileStatic
@PackageScope abstract class SlimeLauncherExec extends JavaExec implements ForgeGradleTask {
    @PackageScope static TaskProvider<SlimeLauncherExec> register(Project project, SourceSet sourceSet, SlimeLauncherOptions options, Map<String, RunConfig> configs, Dependency dependency, Provider<RegularFile> metadataZip) {
        project.tasks.register(sourceSet.getTaskName('run', options.name), SlimeLauncherExec) { task ->
            task.description = "Runs the '$options.name' Slime Launcher run configuration."

            task.classpath = task.objectFactory.fileCollection().from(
                task.providerFactory.provider { sourceSet.runtimeClasspath }
            )

            var caches = task.objectFactory.directoryProperty().value(task.globalCaches.dir("slime-launcher/cache/${dependency.group.replace('.', '/')}/${dependency.name}/${dependency.version}"))
            task.cacheDir.set caches.map(task.problems.ensureDirectory())
            task.metadataZip.set metadataZip

            task.inherit(configs, options.name)
            options.apply(task)

            task.classpath task.getTool(Tools.SLIME_LAUNCHER, project)

            if (task.buildAllProjects)
                task.dependsOn project.allprojects.collect { it.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) }.toArray()
        }
    }

    private final ForgeGradleProblems problems

    private boolean buildAllProjects

    @Inject
    SlimeLauncherExec(Problems problems) {
        this.problems = new ForgeGradleProblems(problems, this.providerFactory)

        this.group = 'Slime Launcher'

        this.modularity.inferModulePath.set false
        this.mainClass.convention Constants.SLIMELAUNCHER_MAIN
        this.javaLauncher.convention Util.launcherFor(this.project.extensions.getByType(JavaPluginExtension), this.javaToolchainService, Constants.SLIMELAUNCHER_JAVA_VERSION)
    }

    private void inherit(Map<String, RunConfig> configs, String name) {
        final config = configs.get name
        if (config === null) return

        if (config.parents !== null && !config.parents.empty)
            config.parents.forEach { parent -> this.inherit configs, parent }

        if (config.main !== null)
            this.bootstrapMainClass.set config.main

        if (config.args !== null && !config.args.empty)
            this.mcBootstrapArgs.set new ArrayList<>(config.args)

        if (config.jvmArgs !== null && !config.jvmArgs.empty)
            this.jvmArgs config.jvmArgs

        this.client.set config.client

        this.buildAllProjects = config.buildAllProjects

        if (config.env !== null && !config.env.isEmpty())
            this.environment config.env

        if (config.props !== null && !config.props.isEmpty())
            this.systemProperties config.props
    }

    @Override
    void exec() {
        if (!this.mainClass.get().startsWith('net.minecraftforge.launcher')) {
            this.logger.lifecycle 'Main class is not Slime Launcher! Skipping additional configuration.'
        } else {
            this.args('--main', this.bootstrapMainClass.get(),
                '--cache', this.cacheDir.get().asFile.absolutePath,
                '--metadata', this.metadataZip.get().asFile.absolutePath,
                '--')

            this.args(this.mcBootstrapArgs.getOrElse(List.of()).toArray())
        }

        Files.createDirectories(this.workingDir.toPath())

        super.exec()
    }

    /** The cache dir for Slime Launcher to extract and work with metadata. */
    abstract @InputDirectory DirectoryProperty getCacheDir()
    /** The location of the {@code metadata.zip} artifact produced by the Minecraft Mavenizer. */
    abstract @InputFile RegularFileProperty getMetadataZip()
    abstract @Input Property<String> getBootstrapMainClass()
    abstract @Input @Optional ListProperty<String> getMcBootstrapArgs()
    abstract @Input @Optional Property<Boolean> getClient()
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle


import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskProvider

import javax.inject.Inject

/**
 * This task executes the Minecraft Mavenizer, Forge's standalone tool for generating a local Maven repository for
 * Minecraft artifacts.
 *
 * @see MinecraftExtensionImpl
 */
@CompileStatic
@PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.FIELDS])
abstract class MinecraftMavenExec extends JavaExec {
    /** The name of the task that is used to sync the Minecraft Maven. */
    static final String NAME = 'syncMinecraftMaven'

    @PackageScope static TaskProvider<MinecraftMavenExec> register(Project project, DirectoryProperty globalCaches, Dependency dependency) {
        project.tasks.register(NAME, MinecraftMavenExec) {
            it.group = 'Build Setup'
            it.description = 'Syncs the Minecraft Maven dependencies.'

            it.classpath = it.objectFactory.fileCollection().from(DefaultTools.MINECRAFT_MAVEN.get(globalCaches, it.providerFactory))

            it.cacheDir.set globalCaches.dir('mc-maven/cache').map(it.problems.ensureDirectory())
            it.jdkCacheDir.set globalCaches.dir('mc-maven/cache/jdks').map(it.problems.ensureDirectory())
            it.outputDir.set globalCaches.dir('mc-maven/output').map(it.problems.ensureDirectory())

            it.artifact.set "${dependency.group}:${dependency.name}".toString()
            it.artifactVersion.set dependency.version
        }.tap {
            // TODO [ForgeGradle7][MinecraftMaven] This might cause problems if a consumer manually runs this task with custom arguments.
            //  Consider re-implementing Util#runFirst.
            Util.runFirst project, it
        }
    }

    private final ForgeGradleProblems problems

    /**
     * Creates a new task with the default values.
     *
     * @param classpath The classpath containing the Minecraft Mavenizer.
     */
    @Inject
    MinecraftMavenExec(Problems problems) {
        this.problems = new ForgeGradleProblems(problems, this.providerFactory)

        this.mainClass.convention(Constants.MCMAVEN_MAIN)
        this.javaLauncher.convention(Util.launcherForStrictly(this.javaToolchainService, Constants.MCMAVEN_JAVA_VERSION))
    }

    // TODO [ForgeGradle7][Gradle9] Remove when Gradle makes program args a list property
    @Deprecated(forRemoval = true)
    private List<String> getProgramArgs() {
        [
            '--maven',
            '--cache', this.cacheDir.get().asFile.absolutePath,
            '--jdk-cache', this.jdkCacheDir.get().asFile.absolutePath,
            '--output', this.outputDir.get().asFile.absolutePath,
            '--artifact', this.artifact.get(),
            '--version', this.artifactVersion.get()
        ]
    }

    @Override
    void exec() {
        this.args = this.programArgs

        super.exec()
    }

    /** The directory to store caches such as processed files and downloaded tools. */
    abstract @InputDirectory DirectoryProperty getCacheDir()
    /** The directory to store downloaded JDKs needed to run tools. */
    abstract @InputDirectory DirectoryProperty getJdkCacheDir()
    /** The output directory where the Minecraft Maven will be located. */
    abstract @InputDirectory DirectoryProperty getOutputDir()
    /** The Minecraft artifact to generate (i.e. {@code net.minecraftforge:forge}). */
    abstract @Input Property<String> getArtifact()
    /** The artifact version to use (i.e. {@code 1.21.5}). */
    abstract @Input Property<String> getArtifactVersion()
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.Map;
import java.util.function.Supplier;

@DisableCachingByDefault(because = "Running Minecraft cannot be cached")
abstract class MinecraftRunTask extends JavaExec {
    public MinecraftRunTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
        this.setImpliesSubProjects(true); // Running the game in the current project and child projects is a bad idea

        this.getJavaLauncher().convention(this.getJavaToolchainService().launcherFor(this.getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain()));
    }

    @TaskAction
    @Override
    public void exec() {
        Project project = this.getProject();
        RunConfig runConfig = this.getRunConfig().get();
        File workDir = prepareWorkingDirectory(runConfig);

        Map<String, Supplier<String>> updatedTokens = RunConfigGenerator.configureTokensLazy(project, runConfig,
                RunConfigGenerator.mapModClassesToGradle(project, runConfig),
                this.getMinecraftArtifacts().getFiles(), this.getRuntimeClasspathArtifacts().getFiles());

        this.setWorkingDir(workDir);
        this.args(RunConfigGenerator.getArgsStream(runConfig, updatedTokens, false).toArray());
        runConfig.getJvmArgs().forEach(arg -> this.jvmArgs(runConfig.replace(updatedTokens, arg)));
        if (runConfig.isClient()) {
            getAdditionalClientArgs().get().forEach(arg -> this.jvmArgs(runConfig.replace(updatedTokens, arg)));
        }
        runConfig.getEnvironment().forEach((key, value) -> this.environment(key, runConfig.replace(updatedTokens, value)));
        runConfig.getProperties().forEach((key, value) -> this.systemProperty(key, runConfig.replace(updatedTokens, value)));

        runConfig.getAllSources().stream().map(SourceSet::getRuntimeClasspath).forEach(this::classpath);

        super.exec();
    }

    public static File prepareWorkingDirectory(RunConfig runConfig) {
        File workDir = new File(runConfig.getWorkingDirectory());

        if (!workDir.exists() && !workDir.mkdirs())
            throw new RuntimeException("Could not create working directory: " + workDir.getAbsolutePath());

        return workDir;
    }

    @Input
    public abstract Property<RunConfig> getRunConfig();

    @Input
    public abstract ListProperty<String> getAdditionalClientArgs();

    @InputFiles
    public abstract ConfigurableFileCollection getMinecraftArtifacts();

    @InputFiles
    public abstract ConfigurableFileCollection getRuntimeClasspathArtifacts();
}

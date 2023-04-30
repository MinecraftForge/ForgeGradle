/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.List;
import java.util.Set;

@DisableCachingByDefault(because = "IDE runs should always be regenerated")
abstract class GenIDERunsTask extends DefaultTask {
    public GenIDERunsTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
    }

    @TaskAction
    public void run() {
        File runConfigurationsDir = this.getRunConfigurationsFolder().get().getAsFile();

        if (!runConfigurationsDir.exists() && !runConfigurationsDir.mkdirs())
            throw new RuntimeException("Could not create run configurations directory: " + runConfigurationsDir.getAbsolutePath());

        RunConfigGenerator runConfigGenerator = this.getRunConfigGenerator().get();
        MinecraftExtension minecraft = this.getMinecraftExtension().get();
        Project project = this.getProject();
        List<String> additionalClientArgs = this.getAdditionalClientArgs().get();
        Set<File> minecraftArtifacts = this.getMinecraftArtifacts().getFiles();
        Set<File> runtimeClasspathArtifacts = this.getRuntimeClasspathArtifacts().getFiles();

        runConfigGenerator.createRunConfiguration(minecraft, runConfigurationsDir, project,
                additionalClientArgs, minecraftArtifacts, runtimeClasspathArtifacts);
    }

    @InputDirectory
    public abstract DirectoryProperty getRunConfigurationsFolder();

    @Internal
    public abstract Property<RunConfigGenerator> getRunConfigGenerator();

    @Internal
    public abstract Property<MinecraftExtension> getMinecraftExtension();

    @Input
    public abstract ListProperty<String> getAdditionalClientArgs();

    @InputFiles
    public abstract ConfigurableFileCollection getMinecraftArtifacts();

    @InputFiles
    public abstract ConfigurableFileCollection getRuntimeClasspathArtifacts();
}

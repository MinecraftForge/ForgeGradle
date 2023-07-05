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
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

import java.io.File;
import java.util.List;

@DisableCachingByDefault(because = "IDE runs should always be regenerated")
abstract class GenIDERunsTask extends DefaultTask {
    public GenIDERunsTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
        this.getRunConfigurationsFolderName().set(this.getRunConfigurationsFolder().map(dir -> dir.getAsFile().getAbsolutePath()));
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

        runConfigGenerator.createRunConfiguration(minecraft, runConfigurationsDir, project,
                additionalClientArgs, this.getMinecraftArtifacts(), this.getRuntimeClasspathArtifacts());
    }

    @Internal
    public abstract DirectoryProperty getRunConfigurationsFolder();

    // Gradle doesn't seem to have a good way to declare an input on the location of a path without caring about its contents (or whether it exists).
    // This serves as a workaround to still support up-to-date checking (although this task should always re-run anyways!)
    @Input
    protected abstract Property<String> getRunConfigurationsFolderName();

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

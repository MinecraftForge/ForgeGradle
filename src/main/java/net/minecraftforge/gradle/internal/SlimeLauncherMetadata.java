/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@CacheableTask
abstract class SlimeLauncherMetadata extends DefaultTask implements ForgeGradleTask {
    static TaskProvider<SlimeLauncherMetadata> register(Project project, MinecraftDependencyInternal mcdep) {
        var taskName = "slimeLauncherMetadataFor" + Util.dependencyToCamelCase(mcdep.getModule());
        return project.getTasks().register(taskName, SlimeLauncherMetadata.class, task -> {
            task.setDescription("Extracts the Slime Launcher metadata for '%s'.".formatted(mcdep.toString()));
            task.getMetadataZip().setFrom(mcdep.getMetadataDependency());
        });
    }

    @PathSensitive(PathSensitivity.NONE)
    protected abstract @InputFiles ConfigurableFileCollection getMetadataZip();

    protected abstract @OutputDirectory DirectoryProperty getOutputDirectory();

    protected abstract @OutputFile RegularFileProperty getRunsJson();

    protected abstract @Inject ArchiveOperations getArchiveOperations();

    protected abstract @Inject FileSystemOperations getFileSystemOperations();

    @Inject
    public SlimeLauncherMetadata() {
        this.getOutputDirectory().convention(this.getDefaultOutputDirectory());
        this.getRunsJson().convention(
                this.getOutputDirectory().map(d -> d.dir("launcher").file("runs.json"))
        );
    }

    @TaskAction
    protected void exec() throws IOException {
        var archive = this.getMetadataZip().getSingleFile();
        var outputDir = this.getOutputDirectory().get();

        this.getFileSystemOperations().sync(spec -> {
            spec.from(this.getArchiveOperations().zipTree(archive));
            spec.into(outputDir);
        });

        // Write an empty runs.json if it doesn't exist
        // This happens when using a 'vanilla' Minecraft dependency
        var json = this.getRunsJson().getAsFile().get().toPath();
        if (!Files.exists(json)) {
            Files.createDirectories(json.getParent());
            Files.writeString(json, "{}", StandardCharsets.UTF_8);
        }
    }
}

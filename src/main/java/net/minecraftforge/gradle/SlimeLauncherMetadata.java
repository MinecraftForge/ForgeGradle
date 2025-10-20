/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

abstract class SlimeLauncherMetadata extends DefaultTask implements ForgeGradleTask {
    protected abstract @InputFile RegularFileProperty getMetadataZip();
    protected abstract @OutputFile RegularFileProperty getRunsJson();

    protected abstract @Inject ArchiveOperations getArchiveOperations();

    @Inject
    public SlimeLauncherMetadata() {
        this.getRunsJson().convention(this.getDefaultOutputDirectory().map(d -> d.file("runs.json")));
    }

    @TaskAction
    protected void exec() {
        this.getArchiveOperations().zipTree(this.getMetadataZip())
            .matching(it -> it.include("launcher/**"))
            .visit(file -> {
                try {
                    if (file.getPath().equals("launcher/runs.json")) {
                        Files.copy(
                            file.getFile().toPath(),
                            this.getRunsJson().getAsFile().get().toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES
                        );
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

abstract class SlimeLauncherMetadata extends DefaultTask implements ForgeGradleTask {
    protected abstract @InputFiles ConfigurableFileCollection getMetadata();

    protected abstract @OutputFile RegularFileProperty getRunsJson();

    @Inject
    public SlimeLauncherMetadata() {
        this.getRunsJson().convention(this.getDefaultOutputDirectory().map(d -> d.file("runs.json")));
    }

    @TaskAction
    protected void exec() throws IOException {
        var archive = this.getMetadata().getSingleFile();
        var json = this.getRunsJson().getAsFile().get().toPath();

        boolean foundRuns = false;
        try (var zin = new ZipInputStream(new FileInputStream(archive))) {
            for (ZipEntry entry; ((entry = zin.getNextEntry()) != null); ) {
                if (!entry.getName().startsWith("launcher/"))
                    continue;
                if (entry.getName().equals("launcher/runs.json")) {
                    Files.copy(zin, json, StandardCopyOption.REPLACE_EXISTING);
                    foundRuns = true;
                }
            }
        }

        // If we don't find a metadata file, write an empty runs
        // This happens when using a 'vanilla' minecraft dependency
        if (!foundRuns)
            Files.writeString(json, "{}", StandardCharsets.UTF_8);
    }
}

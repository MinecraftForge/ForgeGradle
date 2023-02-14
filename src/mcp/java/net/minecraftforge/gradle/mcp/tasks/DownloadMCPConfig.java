/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.tasks;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public abstract class DownloadMCPConfig extends DefaultTask {
    @TaskAction
    public void downloadMCPConfig() throws IOException {
        File file = getConfigFile();
        File output = getOutput().get().getAsFile();

        if (output.exists()) {
            if (FileUtils.contentEquals(file, output)) {
                // NO-OP: The contents of both files are the same, we're up to date
                setDidWork(false);
                return;
            } else {
                output.delete();
            }
        }
        FileUtils.copyFile(file, output);
        setDidWork(true);
    }

    @Input
    public abstract Property<String> getConfig();

    @Internal
    public File getConfigFile() {
        return downloadConfigFile(getConfig().get());
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.manual(getProject(), config, false);
    }
}

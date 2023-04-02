/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.tasks;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPRepo;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public abstract class DownloadMCPMappings extends DefaultTask {
    public DownloadMCPMappings() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().file("mappings.zip"));
    }

    @TaskAction
    public void download() throws IOException {
        File out = getMappingFile();
        File output = getOutput().get().getAsFile();
        this.setDidWork(out.exists());
        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (output.getParentFile() != null && !output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }

    private File getMappingFile() {
        String mappings = getMappings().get();
        int idx = Utils.getMappingSeparatorIdx(mappings);
        if (idx == -1)
            throw new IllegalArgumentException("Invalid mapping string format, must be {channel}_{version}.");
        String channel = mappings.substring(0, idx);
        String version = mappings.substring(idx + 1);
        String artifact = MCPRepo.getMappingDep(channel, version);
        File ret = MavenArtifactDownloader.generate(getProject(), artifact, false);
        if (ret == null)
            throw new IllegalStateException("Failed to download mappings: " + artifact);
        return ret;
    }

    @Input
    public abstract Property<String> getMappings();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

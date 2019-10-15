/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.mcp.MCPRepo;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMCPMappingsTask extends DefaultTask {

    private String mappings;

    private File output = getProject().file("build/mappings.zip");

    @Input
    public String getMappings() {
        return this.mappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setMappings(String value) {
        this.mappings = value;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @TaskAction
    public void download() throws IOException {
        File out = getMappingFile();
        if (out != null && out.exists()) {
            this.setDidWork(true);
        } else {
            this.setDidWork(false);
        }
        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }

    private File getMappingFile() {
        int idx = getMappings().lastIndexOf('_');
        if (idx == -1)
            throw new IllegalArgumentException("Invalid mapping string format, must be {channel}_{version}.");
        String channel = getMappings().substring(0, idx);
        String version = getMappings().substring(idx + 1);
        String artifact = MCPRepo.getMappingDep(channel, version);
        File ret = MavenArtifactDownloader.generate(getProject(), artifact, false);
        if (ret == null)
            throw new IllegalStateException("Failed to download mappings: " + artifact);
        return ret;
    }

}

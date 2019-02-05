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
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMCPConfigTask extends DefaultTask {

    private String config;
    private File output;

    @TaskAction
    public void downloadMCPConfig() throws IOException {
        File file = getConfigFile();

        if (getOutput().exists()) {
            if (FileUtils.contentEquals(file, getOutput())) {
                // NO-OP: The contents of both files are the same, we're up to date
                setDidWork(false);
                return;
            } else {
                getOutput().delete();
            }
        }
        FileUtils.copyFile(file, getOutput());
        setDidWork(true);
    }

    public Object getConfig() {
        return this.config;
    }

    @InputFile
    private File getConfigFile() {
        return downloadConfigFile(config);
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setConfig(String value) {
        this.config = value;
    }

    public void setOutput(File value) {
        this.output = value;
    }

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.manual(getProject(), config, false);
    }

}

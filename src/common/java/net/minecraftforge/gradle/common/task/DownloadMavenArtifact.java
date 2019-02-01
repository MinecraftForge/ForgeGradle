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

package net.minecraftforge.gradle.common.task;

import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMavenArtifact extends DefaultTask {

    private boolean changing = false;
    private String artifact;
    private Artifact _artifact;

    private File output;

    public DownloadMavenArtifact() {
        getOutputs().upToDateWhen(task -> false); //We need to always ask, in case the file on maven/our local MinecraftRepo has changed.
    }

    public String getResolvedVersion() {
        return MavenArtifactDownloader.getVersion(getProject(), _artifact.getDescriptor());
    }

    @Input
    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String value) {
        this.artifact = value;
        this._artifact = Artifact.from(value);
    }

    @Input
    public boolean getChanging() {
        return changing;
    }

    public void setChanging(boolean value) {
        this.changing = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            output = getProject().file("build/" + getName() + "/output." + _artifact.getExtension());
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @TaskAction
    public void run() throws IOException {
        File out = MavenArtifactDownloader.download(getProject(), _artifact.getDescriptor(), getChanging());
        this.setDidWork(out != null && out.exists());

        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }
}

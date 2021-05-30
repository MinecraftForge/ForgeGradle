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

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Artifact;
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

public abstract class DownloadMavenArtifact extends DefaultTask {
    private boolean changing = false;

    public DownloadMavenArtifact() {
        // We need to always ask, in case the file on the remote maven/local fake repo has changed.
        getOutputs().upToDateWhen(task -> false);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName())
                        .zip(getArtifact(), (d, a) -> d.file("output." + a.getExtension())));
    }

    @Internal
    public String getResolvedVersion() {
        return MavenArtifactDownloader.getVersion(getProject(), getArtifact().get().getDescriptor());
    }

    @Input
    public abstract Property<Artifact> getArtifact();

    public void setArtifact(String value) {
        getArtifact().set(Artifact.from(value));
    }

    @Input
    public boolean getChanging() {
        return changing;
    }

    public void setChanging(boolean value) {
        this.changing = value;
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @TaskAction
    public void run() throws IOException {
        File out = MavenArtifactDownloader.download(getProject(), getArtifact().get().getDescriptor(), getChanging());
        this.setDidWork(out != null && out.exists());

        File output = getOutput().get().getAsFile();
        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }
}

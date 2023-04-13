/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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
    private final Property<Artifact> artifact;
    private boolean changing = false;

    public DownloadMavenArtifact() {
        // We need to always ask, in case the file on the remote maven/local fake repo has changed.
        getOutputs().upToDateWhen(task -> false);

        this.artifact = getProject().getObjects().property(Artifact.class);
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName())
                        .zip(getArtifact(), (d, a) -> d.file("output." + a.getExtension())));
    }

    @Internal
    public String getResolvedVersion() {
        return MavenArtifactDownloader.getVersion(getProject(), getArtifact().get().getDescriptor());
    }

    @Input
    public Property<Artifact> getArtifact() {
        return this.artifact;
    }

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

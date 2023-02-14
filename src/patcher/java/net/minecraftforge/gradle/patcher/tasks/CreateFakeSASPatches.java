/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class CreateFakeSASPatches extends DefaultTask {
    public CreateFakeSASPatches() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.dir("patches")));
    }

    @InputFiles
    public abstract ConfigurableFileCollection getFiles();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void apply() throws IOException {
        File output = getOutput().get().getAsFile();
        if (output.exists())
            output.mkdirs();
        for (File file : getFiles()) {
            for (String line : FileUtils.readLines(file, StandardCharsets.UTF_8)) {
                int idx = line.indexOf('#');
                if (idx == 0 || line.isEmpty()) continue;
                if (idx != -1) line = line.substring(0, idx - 1);
                if (line.charAt(0) == '\t') line = line.substring(1);
                String cls = (line.trim() + "    ").split(" ", -1)[0];
                File patch = new File(output, cls + ".java.patch");
                if (!patch.getParentFile().exists())
                    patch.getParentFile().mkdirs();
                patch.createNewFile();
            }
        }
    }
}

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

package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

public class CreateFakeSASPatches extends DefaultTask {
    private List<Supplier<File>> files = new ArrayList<>();
    private Supplier<File> output = () -> getProject().file("build/" + getName() + "/patches/");

    @InputFiles
    public List<File> getFiles() {
        return files.stream().map(Supplier::get).collect(Collectors.toList());
    }
    public void addFile(File value) {
        addFile(() -> value);
    }
    public void addFile(Supplier<File> value) {
        files.add(value);
    }

    @OutputDirectory
    public File getOutput() {
        return output.get();
    }
    public void setOutput(File value) {
        setOutput(() -> value);
    }
    public void setOutput(Supplier<File> value) {
        this.output = value;
    }


    @TaskAction
    public void apply() throws IOException {
        if (getOutput().exists())
            getOutput().mkdirs();
        for (File file : getFiles()) {
            getProject().getLogger().lifecycle("File: " + file);
            for (String line : FileUtils.readLines(file)) {
                int idx = line.indexOf('#');
                if (idx == 0 || line.isEmpty()) continue;
                if (idx != -1) line = line.substring(0, idx - 1);
                if (line.charAt(0) == '\t') line = line.substring(1);
                String cls = (line.trim() + "    ").split(" ", -1)[0];
                File patch = new File(getOutput(), cls + ".java.patch");
                if (!patch.getParentFile().exists())
                    patch.getParentFile().mkdirs();
                patch.createNewFile();
            }
        }
    }
}

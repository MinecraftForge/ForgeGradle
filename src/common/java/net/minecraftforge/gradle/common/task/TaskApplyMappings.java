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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.Utils;

public class TaskApplyMappings extends DefaultTask {
    private File mappings;
    private File input;
    private File output = getProject().file("build/" + getName() + "/output.zip");

    @TaskAction
    public void apply() throws IOException {
        McpNames names = McpNames.load(getMappings());

        try (ZipFile zin = new ZipFile(getInput())) {
            try (FileOutputStream fos = new FileOutputStream(getOutput());
                 ZipOutputStream out = new ZipOutputStream(fos)) {
                zin.stream().forEach(e -> {
                    try {
                        out.putNextEntry(Utils.getStableEntry(e.getName()));
                        if (!e.getName().endsWith(".java")) {
                            IOUtils.copy(zin.getInputStream(e), out);
                        } else {
                            out.write(names.rename(zin.getInputStream(e), false).getBytes(StandardCharsets.UTF_8));
                        }
                        out.closeEntry();
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }
        }
    }

    @InputFile
    public File getInput() {
        return input;
    }

    @InputFile
    public File getMappings() {
        return mappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setInput(File clean) {
        input = clean;
    }

    public void setMappings(File value) {
        mappings = value;
    }

    public void setOutput(File value) {
        output = value;
    }
}

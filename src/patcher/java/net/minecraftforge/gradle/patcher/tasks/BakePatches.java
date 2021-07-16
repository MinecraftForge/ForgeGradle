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

package net.minecraftforge.gradle.patcher.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.InputPath;
import codechicken.diffpatch.util.OutputPath;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

/**
 * Bakes Auto-Header patch files.
 */
public abstract class BakePatches extends DefaultTask {

    public BakePatches() {
        getLineEnding().convention(System.lineSeparator());
    }

    @SuppressWarnings("deprecation")
    @TaskAction
    public void doTask() throws IOException {
        File output = getOutput().get().getAsFile();
        ArchiveFormat outputFormat = ArchiveFormat.findFormat(output.getName());
        if (!getInput().isPresent()) {
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
                // Open and close to make empty zip
                // This is a dumb workaround so :clean:userdevJar doesn't explode
            }
            return;
        }
        PatchOperation.bakePatches(new InputPath.FilePath(getInput().get().getAsFile().toPath(), null),
                new OutputPath.FilePath(output.toPath(), outputFormat), getLineEnding().get());
    }

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<String> getLineEnding();
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.LibraryDownload;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public abstract class ExtractNatives extends DefaultTask {
    @TaskAction
    public void run() throws IOException {
        VersionJson json = Utils.loadJson(getMeta().get().getAsFile(), VersionJson.class);
        for (LibraryDownload lib : json.getNatives()) {
            File target = Utils.getCache(getProject(), "libraries", lib.path);
            Utils.updateDownload(getProject(), target, lib);
            Utils.extractZip(target, getOutput().get().getAsFile(), false, false, name -> {
                if (name.startsWith("META-INF/")) return null;
                // lwjgl libraries >3.2.1 have the natives in directories, we just need the files themselves so the loader can find them
                int idx = name.lastIndexOf('/');
                return idx == -1 ? name : name.substring(idx);
            });
        }
    }

    @InputFile
    public abstract RegularFileProperty getMeta();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();
}

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
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.LibraryDownload;

public class ExtractNatives extends DefaultTask {
    private File meta;
    private File output;

    @TaskAction
    public void run() throws IOException {
        VersionJson json = Utils.loadJson(getMeta(), VersionJson.class);
        for (LibraryDownload lib : json.getNatives()) {
            File target = Utils.getCache(getProject(), "libraries", lib.path);
            Utils.updateDownload(getProject(), target, lib);
            Utils.extractZip(target, getOutput(), false);
        }
    }

    @InputFile
    public File getMeta() {
        return this.meta;
    }
    public void setMeta(File value) {
        this.meta = value;
    }

    @OutputDirectory
    public File getOutput() {
        return this.output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

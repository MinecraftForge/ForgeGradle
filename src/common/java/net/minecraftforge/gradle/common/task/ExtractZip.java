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

public class ExtractZip extends DefaultTask {
    private File zip;
    private File output;

    public ExtractZip() {
        getOutputs().upToDateWhen(task -> false); //Gradle considers this up to date if the output exists at all...
    }

    @TaskAction
    public void run() throws IOException {
        Utils.extractZip(getZip(), getOutput(), true, true);
    }

    @InputFile
    public File getZip() {
        return this.zip;
    }
    public void setZip(File value) {
        this.zip = value;
    }

    @OutputDirectory
    public File getOutput() {
        return this.output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

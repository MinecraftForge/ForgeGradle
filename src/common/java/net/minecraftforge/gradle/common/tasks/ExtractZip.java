/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class ExtractZip extends DefaultTask {
    public ExtractZip() {
        getOutputs().upToDateWhen(task -> false); //Gradle considers this up to date if the output exists at all...
    }

    @TaskAction
    public void run() throws IOException {
        Utils.extractZip(getZip().get().getAsFile(), getOutput().get().getAsFile(), true, true);
    }

    @InputFile
    public abstract RegularFileProperty getZip();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();
}

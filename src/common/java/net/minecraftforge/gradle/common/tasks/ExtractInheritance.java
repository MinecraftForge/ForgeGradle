/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.util.List;

public abstract class ExtractInheritance extends JarExec {
    public ExtractInheritance() {
        getTool().set(Utils.INSTALLERTOOLS);
        getArgs().addAll("--task", "extract_inheritance", "--input", "{input}", "--output", "{output}");

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.json")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        List<String> newArgs = replaceArgs(args, ImmutableMap.of(
                "{input}", getInput().get().getAsFile(),
                "{output}", getOutput().get().getAsFile()), null);
        getLibraries().get().forEach(f -> {
            newArgs.add("--lib");
            newArgs.add(f.getAbsolutePath());
        });
        return newArgs;
    }


    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFiles
    public abstract ListProperty<File> getLibraries();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

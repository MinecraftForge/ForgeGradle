/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public abstract class GenerateBinPatches extends JarExec {
    public GenerateBinPatches() {
        getTool().set(Utils.BINPATCHER);
        getArgs().addAll("--clean", "{clean}", "--create", "{dirty}", "--output", "{output}",
                "--patches", "{patches}", "--srg", "{srg}");

        getOutput().convention(getProject().getLayout().getBuildDirectory()
                .dir(getName()).map(d -> d.file(getSide().getOrElse("output") + ".lzma")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        final List<String> newArgs = replaceArgs(args, ImmutableMap.of(
                "{clean}", getCleanJar().get().getAsFile(),
                "{dirty}", getDirtyJar().get().getAsFile(),
                "{output}", getOutput().get().getAsFile(),
                "{srg}", getSrg().get().getAsFile()
                ), ImmutableMap.of(
                "{patches}", getPatchSets().getFiles()
                )
        );
        return newArgs;
    }

    @InputFile
    public abstract RegularFileProperty getCleanJar();

    @InputFile
    public abstract RegularFileProperty getDirtyJar();

    @InputFiles
    public abstract ConfigurableFileCollection getPatchSets();

    @InputFile
    public abstract RegularFileProperty getSrg();

    @Input
    @Optional
    public abstract Property<String> getSide();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

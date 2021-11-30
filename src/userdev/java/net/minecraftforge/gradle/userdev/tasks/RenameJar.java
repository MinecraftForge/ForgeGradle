/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import java.util.List;

public abstract class RenameJar extends JarExec {
    public RenameJar() {
        getTool().set(Utils.FART);
        getArgs().addAll("--input", "{input}", "--output", "{output}", "--names", "{mappings}", "--ann-fix", "--ids-fix", "--src-fix", "--record-fix");
    }

    protected List<String> filterArgs(List<String> args) {
        return replaceArgsMulti(args, ImmutableMap.of(
                "{input}", getInput().get().getAsFile(),
                "{output}", getOutput().get().getAsFile()
                ), ImmutableMultimap.<String, Object>builder()
                        .put("{mappings}", getMappings().get().getAsFile())
                        .putAll("{mappings}", getExtraMappings().getFiles()).build()
        );
    }

    // TODO: Make this a ConfigurableFileCollection? (then remove getExtraMappings())
    @InputFile
    public abstract RegularFileProperty getMappings();

    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getExtraMappings();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

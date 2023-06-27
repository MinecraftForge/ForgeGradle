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
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.work.NormalizeLineEndings;

import java.util.List;

@CacheableTask
public abstract class AccessTransformJar extends JarExec {
    public AccessTransformJar() {
        getTool().set(Utils.ACCESSTRANSFORMER);
        getArgs().addAll("--inJar", "{input}", "--outJar", "{output}", "--logFile", "accesstransform.log");
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        List<String> newArgs = replaceArgs(args, ImmutableMap.of(
                "{input}", getInput().get().getAsFile().getAbsolutePath(),
                "{output}", getOutput().get().getAsFile().getAbsolutePath()), null);
        getAccessTransformers().forEach(f -> {
            newArgs.add("--atFile");
            newArgs.add(f.getAbsolutePath());
        });
        return newArgs;
    }

    @InputFiles
    @SkipWhenEmpty
    @NormalizeLineEndings
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAccessTransformers();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

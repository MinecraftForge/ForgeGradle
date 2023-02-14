/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public abstract class ApplyBinPatches extends JarExec {
    public ApplyBinPatches() {
        getTool().set(Utils.BINPATCHER);
        getArgs().addAll("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{clean}", getClean().get().getAsFile(),
                "{output}", getOutput().get().getAsFile(),
                "{patch}", getPatch().get().getAsFile()), null);
    }

    @InputFile
    public abstract RegularFileProperty getClean();

    @InputFile
    public abstract RegularFileProperty getPatch();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

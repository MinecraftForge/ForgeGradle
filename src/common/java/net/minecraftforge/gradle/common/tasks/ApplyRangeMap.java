/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import java.util.List;

public abstract class ApplyRangeMap extends JarExec {
    public boolean annotate = false;
    public boolean keepImports = true;

    public ApplyRangeMap() {
        getTool().set(Utils.SRG2SOURCE);
        getArgs().addAll("--apply", "--input", "{input}", "--range", "{range}", "--srg", "{srg}", "--exc", "{exc}",
                "--output", "{output}", "--keepImports", "{keepImports}");
        setMinimumRuntimeJavaVersion(11);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.zip")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{range}", getRangeMap().get().getAsFile(),
                "{output}", getOutput().get().getAsFile(),
                "{annotate}", annotate,
                "{keepImports}", keepImports
                ), ImmutableMap.of(
                "{input}", getSources().getFiles(),
                "{srg}", getSrgFiles().getFiles(),
                "{exc}", getExcFiles().getFiles()
                )
        );
    }

    @InputFiles
    public abstract ConfigurableFileCollection getSrgFiles();

    @InputFiles
    public abstract ConfigurableFileCollection getSources();

    @InputFiles
    public abstract ConfigurableFileCollection getExcFiles();

    @InputFile
    public abstract RegularFileProperty getRangeMap();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public boolean getAnnotate() {
        return annotate;
    }

    public void setAnnotate(boolean value) {
        this.annotate = value;
    }

    @Input
    public boolean getKeepImports() {
        return keepImports;
    }

    public void setKeepImports(boolean value) {
        this.keepImports = value;
    }
}

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

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;

import javax.inject.Inject;
import java.util.List;

public abstract class ApplyRangeMap extends JarExec {
    public boolean annotate = false;
    public boolean keepImports = true;

    public ApplyRangeMap() {
        getTool().set(Utils.SRG2SOURCE);
        getArgs().addAll("--apply", "--input", "{input}", "--range", "{range}", "--srg", "{srg}", "--exc", "{exc}",
                "--output", "{output}", "--keepImports", "{keepImports}");
        setMinimumRuntimeJavaVersion(11);

        getOutput().convention(getProjectLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.zip")));
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

    @Inject
    protected abstract ProjectLayout getProjectLayout();

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

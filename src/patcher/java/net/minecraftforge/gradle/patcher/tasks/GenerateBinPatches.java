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

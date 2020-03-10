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

package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Deprecated  //Remove next major version, this is a duplicate of userdev ApplyBinPatches
public class ApplyBinPatches extends JarExec {
    private Supplier<File> clean;
    private File input;
    private File output;

    public ApplyBinPatches() {
        tool = Utils.BINPATCHER;
        args = new String[] { "--clean", "{clean}", "--output", "{output}", "--apply", "{input}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{clean}", getClean().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{input}", getInput().getAbsolutePath());

        return Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

    @InputFile
    public File getClean() {
        return clean.get();
    }
    public void setClean(File value) {
        this.clean = () -> value;
    }
    public void setClean(Supplier<File> value) {
        this.clean = value;
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            setOutput(getProject().file("build/" + getName() + "/output.jar"));
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

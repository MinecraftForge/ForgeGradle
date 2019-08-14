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

package net.minecraftforge.gradle.common.task;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.gradle.api.tasks.*;

import net.minecraftforge.gradle.common.util.Utils;

public class ExtractInheritance extends JarExec {
    public ExtractInheritance() {
        tool = Utils.INSTALLERTOOLS;
        args = new String[] { "--task", "extract_inheritance", "--input", "{input}", "--output", "{output}"};
    }
    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());

        List<String> ret = Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
        getLibraries().forEach(f -> {
            ret.add("--lib");
            ret.add(f.getAbsolutePath());
        });
        return ret;
    }


    private Supplier<File> input;
    @InputFile
    public File getInput(){ return input == null ? null : input.get(); }
    public void setInput(Supplier<File> v) { input = v; }
    public void input(Supplier<File> v) { setInput(v); }
    public void setInput(File v){ setInput(() -> v); }
    public void input(File v) { setInput(v); }

    private List<Supplier<File>> libraries = new ArrayList<>();
    @InputFiles
    public List<File> getLibraries() { return libraries.stream().map(Supplier::get).collect(Collectors.toList()); }
    public void addLibrary(Supplier<File> v){ libraries.add(v); }
    public void library(Supplier<File> v) { addLibrary(v); }
    public void addLibrary(File lib){ addLibrary(() -> lib); }
    public void library(File v) { addLibrary(v); }

    private Supplier<File> output = () -> getProject().file("build/" + getName() + "/output.json");
    @OutputFile
    public File getOutput(){ return output.get(); }
    public void setOutput(Supplier<File> v){ output = v; }
    public void output(Supplier<File> v){ setOutput(v); }
    public void setOutput(File v){ setInput(() -> v); }
    public void output(File v){ setOutput(v); }
}

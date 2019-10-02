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

package net.minecraftforge.gradle.userdev.tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

public class RenameJarInPlace extends JarExec {
    private Supplier<File> input;
    private File temp;
    private Supplier<File> mappings;
    private List<Supplier<File>> extraMappings;

    public RenameJarInPlace() {
        tool = Utils.SPECIALSOURCE;
        args = new String[] { "--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{mappings}", "--live"};
        this.getOutputs().upToDateWhen(task -> false);
    }

    @Override
    protected List<String> filterArgs() {

        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", temp.getAbsolutePath());

        List<String> _args = new ArrayList<>();
        for (String arg : getArgs()) {
            if ("{mappings}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.add(getMappings().getAbsolutePath());

                getExtraMappings().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }

        return _args;
    }

    @Override
    @TaskAction
    public void apply() throws IOException {
        temp = getProject().file("build/" + getName() + "/output.jar");
        if (!temp.getParentFile().exists())
            temp.getParentFile().mkdirs();

        super.apply();

        FileUtils.copyFile(temp, getInput());
    }

    @InputFile
    public File getMappings() {
        return mappings.get();
    }
    public void setMappings(Supplier<File> value) {
        this.mappings = value;
    }
    public void setMappings(File value) {
        this.mappings(value);
    }
    public void mappings(File value) {
        this.mappings(() -> value);
    }
    public void mappings(Supplier<File> value) {
        this.setMappings(value);
    }

    @Optional
    @InputFiles
    public List<File> getExtraMappings() {
        return this.extraMappings == null ? Collections.emptyList() : this.extraMappings.stream().map(Supplier::get).collect(Collectors.toList());
    }
    public void setExtraMappingsDelayed(Collection<Supplier<File>> values) {
        List<Supplier<File>> _new = new ArrayList<>();
        values.forEach(_new::add);
        this.extraMappings = _new;
    }
    public void setExtraMappings(Collection<File> values) {
        List<Supplier<File>> _new = new ArrayList<>();
        values.stream().forEach(f  -> _new.add(() -> f));
        this.extraMappings = _new;
    }
    public void extraMapping(File value) {
        this.extraMapping(() -> value);
    }
    public void extraMapping(Supplier<File> value) {
        if (this.extraMappings == null)
            this.extraMappings = new ArrayList<>();
        this.extraMappings.add(value);
    }

    @InputFile
    public File getInput() {
        return input.get();
    }
    public void setInput(Supplier<File> value) {
        this.input = value;
    }
    public void setInput(File value) {
        this.setInput(() -> value);
    }
    public void input(File value) {
        this.input(() -> value);
    }
    public void input(Supplier<File> value) {
        this.setInput(value);
    }
}

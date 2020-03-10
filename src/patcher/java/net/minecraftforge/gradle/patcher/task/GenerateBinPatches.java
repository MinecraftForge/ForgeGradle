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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenerateBinPatches extends JarExec {
    private File cleanJar;
    private File dirtyJar;
    private File srg;
    private Set<File> patchSets = new HashSet<>();
    private String side;
    private File output = null;
    private Map<String, File> extras = new HashMap<>();

    public GenerateBinPatches() {
        tool = Utils.BINPATCHER;
        args = new String[] { "--clean", "{clean}", "--create", "{dirty}", "--output", "{output}", "--patches", "{patches}", "--srg", "{srg}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{clean}", getCleanJar().getAbsolutePath());
        replace.put("{dirty}", getDirtyJar().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{srg}", getSrg().getAbsolutePath());
        this.extras.forEach((k,v) -> replace.put('{' + k + '}', v.getAbsolutePath()));

        List<String> _args = new ArrayList<>();
        for (String arg : getArgs()) {
            if ("{patches}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getPatchSets().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }
        return _args;
    }

    @InputFile
    public File getCleanJar() {
        return cleanJar;
    }
    public void setCleanJar(File value) {
        this.cleanJar = value;
    }

    @InputFile
    public File getDirtyJar() {
        return dirtyJar;
    }
    public void setDirtyJar(File value) {
        this.dirtyJar = value;
    }

    @InputFiles
    public Set<File> getPatchSets() {
        return this.patchSets;
    }
    public void setPatchSets(Set<File> values) {
        this.patchSets = values;
    }
    public void addPatchSet(File value) {
        if (value != null) {
            this.patchSets.add(value);
        }
    }

    @InputFiles
    @Optional
    public Collection<File> getExtraFiles() {
        return this.extras.values();
    }
    public void addExtra(String key, File value) {
        this.extras.put(key, value);
    }

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Input
    @Optional
    public String getSide() {
        return this.side;
    }
    public void setSide(String value) {
        this.side = value;
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/" + getSide() + ".lzma"));
        }
    }

    @OutputFile
    public File getOutput() {
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/output.lzma"));
        }
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

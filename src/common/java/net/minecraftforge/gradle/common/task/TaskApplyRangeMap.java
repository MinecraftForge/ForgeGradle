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

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
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

public class TaskApplyRangeMap extends JarExec {

    //private Set<String> srgExtra = new HashSet<>(); //TODO: Make S2S read strings easier
    //private Set<String> excExtra = new HashSet<>(); //TODO: Make S2S read strings easier
    private Set<File> srgs = new HashSet<>();
    private Set<File> excs = new HashSet<>();
    private Set<File> sources = new HashSet<>();

    private File rangeMap;
    public boolean annotate = false;
    public boolean keepImports = true;

    private File output = getProject().file("build/" + getName() + "/output.zip");

    public TaskApplyRangeMap() {
        tool = Utils.SRG2SOURCE;
        args = new String[] { "--apply", "--input", "{input}", "--range", "{range}", "--srg", "{srg}", "--exc", "{exc}", "--output", "{output}", "--keepImports", "{keepImports}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{range}", getRangeMap().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{annotate}", getAnnotate() ? "true" : "false");
        replace.put("{keepImports}", getKeepImports() ? "true" : "false");

        List<String> _args = new ArrayList<>();
        for (String arg : getArgs()) {
            if ("{input}".equals(arg))
                expand(_args, getSources());
            else if ("{srg}".equals(arg))
                expand(_args, getSrgFiles());
            else if ("{exc}".equals(arg))
                expand(_args, getExcFiles());
            else
                _args.add(replace.getOrDefault(arg, arg));
        }
        return _args;
    }

    private void expand(List<String> _args, Collection<File> files)
    {
        String prefix = _args.get(_args.size() - 1);
        _args.remove(_args.size() - 1);
        files.forEach(f -> {
            _args.add(prefix);
            _args.add(f.getAbsolutePath());
        });
    }

    @InputFiles
    public Set<File> getSrgFiles() {
        return this.srgs;
    }
    public void setSrgFiles(File... values) {
        for (File value : values) {
            this.srgs.add(value);
        }
    }
    /*
    @Input
    public Set<String> getSrgExtra() {
        return this.srgExtra;
    }
    public void setSrg(String... values) {
        for (String val : values) {
            this.srgExtra.add(val);
        }
    }
    */

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }
    public void setSources(Collection<File> values) {
        this.sources.addAll(values);
    }
    public void setSources(File... values) {
        for (File value : values) {
            this.sources.add(value);
        }
    }
    public void sources(File... values) {
        setSources(values);
    }
    public void sources(Collection<File> values) {
        setSources(values);
    }

    @InputFile
    public File getRangeMap() {
        return rangeMap;
    }
    public void setRangeMap(File value) {
        this.rangeMap = value;
    }
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

    @InputFiles
    public Set<File> getExcFiles() {
        return excs;
    }
    public void setExcFiles(File... values) {
        for (File value : values) {
            this.excs.add(value);
        }
    }
    public void setExcFiles(Collection<File> values) {
        this.excs.addAll(values);
    }
    /*
    @Input
    public Set<String> getExcExtra() {
        return this.excExtra;
    }
    public void setExc(String... values) {
        for (String val : values) {
            this.excExtra.add(val);
        }
    }
    */

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

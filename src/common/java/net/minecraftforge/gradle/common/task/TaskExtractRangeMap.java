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

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
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

public class TaskExtractRangeMap extends JarExec {
    private Set<File> sources;
    private Set<FileCollection> dependencies = new HashSet<>();
    private File output = getProject().file("build/" + getName() + "/output.txt");
    private String source_compatibility = "1.8";
    private boolean batch = true;

    public TaskExtractRangeMap() {
        tool = Utils.SRG2SOURCE;
        args = new String[] { "--extract", "--source-compatibility", "{compat}", "--output", "{output}", "--lib", "{library}", "--input", "{input}", "--batch", "{batched}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{compat}", getSourceCompatibility());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{batched}", getBatch() ? "true" : "false");

        List<String> _args = new ArrayList<>();
        for (String arg : getArgs()) {
            if ("{library}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getDependencies().forEach(fc -> {
                   fc.getFiles().forEach(f -> {
                       _args.add(prefix);
                       _args.add(f.getAbsolutePath());
                   });
                });
            } else if ("{input}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getSources().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }
        return _args;
    }

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }
    public void setSources(Set<File> value) {
        this.sources = value;
    }
    public void addSources(Set<File> values) {
        if (this.sources == null)
            this.sources = new HashSet<>();
        this.sources.addAll(values);
    }
    public void sources(Collection<File> values) {
        if (this.sources == null)
            this.sources = new HashSet<>();
        this.sources.addAll(values);
    }
    public void sources(File... values) {
        if (this.sources == null)
            this.sources = new HashSet<>();
        for (File value : values)
            this.sources.add(value);
    }

    @InputFiles
    public Set<FileCollection> getDependencies() {
        return dependencies;
    }
    public void addDependencies(FileCollection values) {
        this.dependencies.add(values);
    }
    public void addDependencies(File... values) {
        for (File dep : values)
            this.dependencies.add(getProject().files(dep));
    }

    @Input
    public String getSourceCompatibility() {
        return this.source_compatibility;
    }
    public void setSourceCompatibility(String value) {
        this.source_compatibility = value;
    }

    @Input
    public boolean getBatch() {
        return this.batch;
    }
    public void setBatch(boolean value) {
        this.batch = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

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

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class TaskFilterNewJar extends DefaultTask { //TODO: Copy task?
    private File input;
    private File srg;
    private Set<File> blacklist = new HashSet<>();
    private File output = getProject().file("build/" + getName() + "/output.jar");

    @TaskAction
    public void apply() throws IOException {
        Set<String> filter = new HashSet<>();
        for (File file : getBlacklist()) {
            try (ZipFile zip = new ZipFile(file)) {
                Utils.forZip(zip, entry -> filter.add(entry.getName()));
            }
        }

        Set<String> classes = new HashSet<>();
        List<String> lines = Files.readLines(getSrg(), StandardCharsets.UTF_8).stream().map(line -> line.split("#")[0]).filter(l -> l != null & !l.trim().isEmpty()).collect(Collectors.toList());
        lines.stream()
        .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
        .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
        .filter(pts -> pts.length == 2 && !pts[0].endsWith("/"))
        .forEach(pts -> classes.add(pts[1]));

        try (ZipFile zin = new ZipFile(getInput());
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(getOutput()))){

            Utils.forZip(zin, entry -> {
                if (entry.isDirectory() || filter.contains(entry.getName()) ||
                    (entry.getName().endsWith(".class") && isVanilla(classes, entry.getName().substring(0, entry.getName().length() - 6)))) {
                    return;
                }
                out.putNextEntry(Utils.getStableEntry(entry.getName()));
                IOUtils.copy(zin.getInputStream(entry), out);
                out.closeEntry();
            });
        }
    }

    //We pack all inner classes in binpatches. So strip anything thats a vanilla class or inner class of one.
    private boolean isVanilla(Set<String> classes, String cls) {
        int idx = cls.indexOf('$');
        if (idx != -1) {
            return isVanilla(classes, cls.substring(0, idx));
        }
        return classes.contains(cls);
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @InputFile
    public File getSrg() {
        return srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @InputFiles
    public Set<File> getBlacklist() {
        return this.blacklist;
    }
    public void setBlacklist(Set<File> value) {
        this.blacklist = value;
    }
    public void addBlacklist(Collection<File> values) {
        this.blacklist.addAll(values);
    }
    public void addBlacklist(File... values) {
        for (File value : values) {
            this.blacklist.add(value);
        }
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

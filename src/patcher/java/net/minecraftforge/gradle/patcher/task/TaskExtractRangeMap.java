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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.ChainedInputSupplier;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class TaskExtractRangeMap extends DefaultTask {

    private Set<File> sources;
    private Set<FileCollection> dependencies = new HashSet<>();
    private File output = getProject().file("build/" + getName() + "/output.txt");
    private File log = getProject().file("build/" + getName() + "/log.txt");

    @TaskAction
    public void extractRangeMap() throws IOException {
        RangeExtractor extract = new RangeExtractor(RangeExtractor.JAVA_1_8);
        for (FileCollection files : getDependencies()) {
            for (File file : files) {
                //getProject().getLogger().lifecycle("Lib: " + file);
                extract.addLibs(file);
            }
        }
        if (getOutput().exists()) {
            try (InputStream fin = new FileInputStream(getOutput())) {
                extract.loadCache(fin);
            }
        }
        extract.setSrc(getInputSupplier());
        try (FileOutputStream fos = new FileOutputStream(log)) {
            extract.setOutLogger(new PrintStream(fos));
            if (!extract.generateRangeMap(getOutput())) {
                throw new RuntimeException("RangeExtractor failed, Check Log for details: " + log);
            }
        }
    }

    @SuppressWarnings("resource")
    private InputSupplier getInputSupplier() throws IOException {
        ChainedInputSupplier inputs = new ChainedInputSupplier();
        for (File src : getSources()) {
            if (src.exists()) {
                inputs.add(src.isDirectory() ? new FolderSupplier(src) : new ZipInputSupplier(src));
            }
        }
        return inputs.shrink();
    }

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }

    @InputFiles
    public Set<FileCollection> getDependencies() {
        return dependencies;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setSources(Set<File> sources) {
        this.sources = sources;
    }
    public void addSources(Set<File> values) {
        if (this.sources == null) {
            this.sources = new HashSet<>();
        }
        this.sources.addAll(values);
    }

    public void addDependencies(FileCollection dependencies) {
        this.dependencies.add(dependencies);
    }

    public void addDependencies(File... dependencies) {
        for (File dep : dependencies) {
            this.dependencies.add(getProject().files(dep));
        }
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

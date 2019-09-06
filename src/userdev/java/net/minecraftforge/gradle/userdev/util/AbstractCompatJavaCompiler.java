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

package net.minecraftforge.gradle.userdev.util;

import com.google.common.collect.ImmutableList;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

abstract class AbstractCompatJavaCompiler implements CompatJavaCompiler {
    private static final PatternFilterable JAVA_FILES = new PatternSet();

    static {
        JAVA_FILES.include("**/*.java");
    }

    private FileTree source;
    private FileCollection classpath;
    private JavaVersion sourceCompatibility = JavaVersion.current();
    private JavaVersion targetCompatibility = JavaVersion.current();
    private File destinationDir;

    /**
     * Filter for warnings not relevant for MC compilation.
     */
    static boolean isSuppressedLine(String line) {
        return line.contains("bootstrap class path not set in conjunction with")
            || line.startsWith("Some input files use")
            || line.startsWith("Recompile with -Xlint:");
    }

    final List<String> createOptions() {
        Set<File> cp = requireNonNull(classpath).getFiles();
        String sourceCompat = requireNonNull(sourceCompatibility).toString();
        String targetCompat = requireNonNull(targetCompatibility).toString();
        String destDir = requireNonNull(destinationDir).getAbsolutePath();

        ImmutableList.Builder<String> options = ImmutableList.builder();
        if (!cp.isEmpty()) {
            options.add("-cp");
            options.add(cp.stream().map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));
        }
        options.add("-source").add(sourceCompat);
        options.add("-target").add(targetCompat);
        options.add("-d").add(destDir);
        return options.build();
    }

    @Override
    public FileCollection getClasspath() {
        return classpath;
    }

    @Override
    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    @Override
    public JavaVersion getSourceCompatibility() {
        return sourceCompatibility;
    }

    @Override
    public void setSourceCompatibility(JavaVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    @Override
    public JavaVersion getTargetCompatibility() {
        return targetCompatibility;
    }

    @Override
    public void setTargetCompatibility(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    @Override
    public File getDestinationDir() {
        return destinationDir;
    }

    @Override
    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    @Override
    public FileTree getSource() {
        return source;
    }

    @Override
    public void setSource(FileTree source) {
        this.source = source.matching(JAVA_FILES);
    }
}

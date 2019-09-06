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

import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;

import java.io.File;

/**
 * Represents access to a common Java compiler interface that works
 * even in the event that {@code javax.tools} or the internal Sun
 * compiler class does not exist.
 */
public interface CompatJavaCompiler {

    void compile();

    FileCollection getClasspath();

    void setClasspath(FileCollection classpath);

    JavaVersion getSourceCompatibility();

    void setSourceCompatibility(JavaVersion sourceCompatibility);

    JavaVersion getTargetCompatibility();

    void setTargetCompatibility(JavaVersion targetCompatibility);

    File getDestinationDir();

    void setDestinationDir(File destinationDir);

    FileTree getSource();

    void setSource(FileTree source);
}

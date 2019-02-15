/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.util;

import java.io.File;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;

public class ExtractionVisitor implements FileVisitor
{
    private final File                  outputDir;
    private final boolean               emptyDirs;
    private final Spec<FileTreeElement> spec;

    public ExtractionVisitor(File outDir, boolean emptyDirs, Spec<FileTreeElement> spec)
    {
        this.outputDir = outDir;
        this.emptyDirs = emptyDirs;
        this.spec = spec;
    }

    @Override
    public void visitDir(FileVisitDetails details)
    {
        if (emptyDirs && spec.isSatisfiedBy(details))
        {
            File dir = new File(outputDir, details.getPath());
            dir.mkdirs();
        }
    }

    @Override
    public void visitFile(FileVisitDetails details)
    {
        if (!spec.isSatisfiedBy(details))
        {
            return;
        }

        File out = new File(outputDir, details.getPath());
        out.getParentFile().mkdirs();
        details.copyTo(out);
    }
}

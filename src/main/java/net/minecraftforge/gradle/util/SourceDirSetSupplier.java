/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.SourceDirectorySet;

import java.io.*;
import java.util.List;
import java.util.Map;

public class SourceDirSetSupplier implements InputSupplier, OutputSupplier
{
    Map<String, File> rootMap = Maps.newHashMap();

    public SourceDirSetSupplier(SourceDirectorySet set)
    {
        set.visit(new FileVisitor() {
            @Override public void visitDir(FileVisitDetails fileVisitDetails) { }

            @Override
            public void visitFile(FileVisitDetails fileVisitDetails) {
                String absolute = fileVisitDetails.getFile().getAbsolutePath();
                String path = fileVisitDetails.getPath();
                File root = new File(absolute.substring(0, absolute.length() - path.length()));
                rootMap.put(path, root);
            }
        });
    }

    @Override
    public String getRoot(String resource)
    {
        return null;
    }

    @Override
    public InputStream getInput(String relPath)
    {
        File f = getFile(relPath);
        try
        {
            return f == null ? null : new FileInputStream(f);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        return Lists.newArrayList(rootMap.keySet());
    }

    @Override
    public void close() throws IOException
    {
        // pointless
    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        File f = getFile(relPath);
        try
        {
            return f == null ? null : new FileOutputStream(f);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private File getFile(String path)
    {
        File root = rootMap.get(path);
        if (root == null)
            return null;
        return new File(root, path);
    }
}

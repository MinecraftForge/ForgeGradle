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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;

import com.google.common.collect.ImmutableList;

public class MultiDirSupplier implements InputSupplier, OutputSupplier
{
    private final List<File> dirs;

    public MultiDirSupplier(Iterable<File> dirs)
    {
        this.dirs = ImmutableList.copyOf(dirs);
    }

    @Override
    public void close() throws IOException
    {
        // nothing to do here...
    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        File f = getFileFor(relPath);
        try
        {
            return f == null ? null : new FileOutputStream(f);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public String getRoot(String resource)
    {
        File dir = getDirFor(resource);
        return dir == null ? null : dir.getAbsolutePath();
    }

    @Override
    public InputStream getInput(String relPath)
    {
        File f = getFileFor(relPath);
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
        // stolen from the FolderSupplier.
        LinkedList<String> out = new LinkedList<String>();
        Stack<File> dirStack = new Stack<File>();

        for (File root : dirs)
        {
            dirStack.push(root);
            int rootCut = root.getAbsolutePath().length() + 1; // +1 for the slash

            while (dirStack.size() > 0)
            {
                for (File f : dirStack.pop().listFiles())
                {
                    if (f.isDirectory())
                        dirStack.push(f);
                    else if (f.getPath().endsWith(endFilter))
                        out.add(f.getAbsolutePath().substring(rootCut));
                }
            }
        }

        return out;
    }

    /**
     * returns null if no such file exists in any of the directories.
     */
    private File getFileFor(String rel)
    {
        for (File dir : dirs)
        {
            File file = new File(dir, rel);
            if (file.exists())
                return file;
        }

        return null;
    }

    /**
     * returns null if no such file exists in any of the directories.
     */
    private File getDirFor(String rel)
    {
        for (File dir : dirs)
        {
            File file = new File(dir, rel);
            if (file.exists())
                return dir;
        }

        return null;
    }
}

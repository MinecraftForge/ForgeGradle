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
package net.minecraftforge.gradle.patcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.util.SequencedInputSupplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

/**
 * The point of this task is to take 2 input sets, and then build a zip/jar containing the files that exist in the 2nd set, but not the first.
 */
class TaskExtractNew extends DefaultTask
{
    //@formatter:off
    private final List<Object>      clean = new LinkedList<Object>();
    private final List<Object>      dirty = new LinkedList<Object>();
    @Input @Optional private String ending;
    @OutputFile      private Object output;
    //@formatter:on

    //@formatter:off
    public TaskExtractNew() { super(); }
    //@formatter:on

    @TaskAction
    public void doStuff() throws IOException
    {
        ending = Strings.nullToEmpty(ending);

        InputSupplier cleanSupplier = getSupplier(getCleanSource());
        InputSupplier dirtySupplier = getSupplier(getDirtySource());

        Set<String> cleanFiles = Sets.newHashSet(cleanSupplier.gatherAll(ending));

        File output = getOutput();
        output.getParentFile().mkdirs();

        boolean isClassEnding = false; //TODO: Figure out Abrar's logic for this... ending.equals(".class"); // this is a trigger for custom stuff

        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output));
        for (String path : dirtySupplier.gatherAll(ending))
        {
            if ( (isClassEnding && matchesClass(cleanFiles, path)) || cleanFiles.contains(path))
            {
                continue;
            }

            zout.putNextEntry(new ZipEntry(path));

            InputStream stream = dirtySupplier.getInput(path);
            ByteStreams.copy(stream, zout);
            stream.close();

            zout.closeEntry();
        }

        zout.close();
        cleanSupplier.close();
        dirtySupplier.close();
    }

    private String stripEnding(String path)
    {
        if (path == null || path.length() < ending.length())
            return null;
        return path.substring(0, path.length() - ending.length());
    }

    private boolean matchesClass(Set<String> cleans, String path)
    {
        int innerIndex = path.indexOf('$');

        if (innerIndex > 0) // better not be starting with $
        {
            // get the parent class, since its an inner
            path = stripEnding(path).substring(0, innerIndex) + ending; // from start till just before $
        }

        return cleans.contains(path);
    }

    private static InputSupplier getSupplier(List<File> files) throws IOException
    {
        SequencedInputSupplier supplier = new SequencedInputSupplier(files.size() + 1);

        for (File f : files)
        {
            if (f.isDirectory())
                supplier.add(new FolderSupplier(f));
            else
            {
                ZipInputSupplier supp = new ZipInputSupplier();
                supp.readZip(f);
                supplier.add(supp);
            }
        }

        return supplier;
    }

    @InputFiles
    public FileCollection getCleanSources()
    {
        return getProject().files(clean);
    }

    public List<File> getCleanSource()
    {
        List<File> files = new LinkedList<File>();
        for (Object f : clean)
            files.add(getProject().file(f));
        return files;
    }

    public void addCleanSource(Object in)
    {
        this.clean.add(in);
    }

    @InputFiles
    public FileCollection getDirtySources()
    {
        return getProject().files(dirty);
    }

    public List<File> getDirtySource()
    {
        List<File> files = new LinkedList<File>();
        for (Object f : dirty)
            files.add(getProject().file(f));
        return files;
    }

    public void addDirtySource(Object in)
    {
        this.dirty.add(in);
    }

    public String getEnding()
    {
        return ending;
    }

    public void setEnding(String ending)
    {
        this.ending = ending;
    }

    public File getOutput()
    {
        return getProject().file(output);
    }

    public void setOutput(Object output)
    {
        this.output = output;
    }
}

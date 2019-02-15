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
package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.SequencedInputSupplier;
import net.minecraftforge.gradle.util.SourceDirSetSupplier;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Lists;

public class ExtractS2SRangeTask extends DefaultTask
{
    @InputFiles
    private List<Object> libs = Lists.newArrayList();

    private final List<Object> sources = Lists.newArrayList();

    @OutputFile
    private Object rangeMap;

    @TaskAction
    public void doTask() throws IOException
    {
        File rangemap = getRangeMap();

        InputSupplier inSup;

        if (sources.size() == 0)
            return; // no input.
        if (sources.size() == 1)
        {
            // just 1 supplier.
            inSup = getInput(sources.get(0));
        }
        else
        {
            // multinput
            inSup = new SequencedInputSupplier();
            for (Object o : sources)
            {
                ((SequencedInputSupplier) inSup).add(getInput(o));
            }
        }

        generateRangeMap(inSup, rangemap);
    }

    private void generateRangeMap(InputSupplier inSup, File rangeMap) throws IOException
    {
        RangeExtractor extractor = new RangeExtractor(RangeExtractor.JAVA_1_8);

        for (File f : getLibs())
        {
            //System.out.println("lib: "+f);
            extractor.addLibs(f);
        }

        if (rangeMap.exists())
        {
            extractor.loadCache(new FileInputStream(rangeMap));
        }

        extractor.setSrc(inSup);

        //extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);

        PrintStream stream = new PrintStream(Constants.getTaskLogStream(getProject(), this.getName() + ".log"));
        extractor.setOutLogger(stream);

        boolean worked = extractor.generateRangeMap(rangeMap);

        stream.close();

        if (!worked)
            throw new RuntimeException("RangeMap generation Failed!!!");
    }

    private InputSupplier getInput(Object o) throws IOException
    {
        if (o instanceof SourceDirectorySet)
        {
            return new SourceDirSetSupplier((SourceDirectorySet) o);
        }

        File f = getProject().file(o);

        System.out.println(f);

        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            ZipInputSupplier supp = new ZipInputSupplier();
            supp.readZip(f);
            return supp;
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories, zips, and SourceDirectorySets right now!");
    }

    public File getRangeMap()
    {
        return getProject().file(rangeMap);
    }

    public void setRangeMap(Object out)
    {
        this.rangeMap = out;
    }

    @InputFiles @SkipWhenEmpty
    public FileCollection getSources()
    {
        FileCollection collection = null;

        for (Object o: this.sources)
        {
            FileCollection col;

            if (o instanceof SourceDirectorySet)
            {
                col = (FileCollection) o;
            }
            else
            {
                File f = getProject().file(o);

                if (f.isDirectory())
                {
                    col = getProject().fileTree(f);
                }
                else
                {
                    col = getProject().files(f);
                }
            }

            if (collection == null)
            {
                collection = col;
            }
            else
            {
                collection = collection.plus(col);
            }
        }

        return collection;
    }

    public void addSource(Object in)
    {
        this.sources.add(in);
    }

    public FileCollection getLibs()
    {
        FileCollection collection = null;

        for (Object o : libs)
        {
            FileCollection col;
            if (o instanceof FileCollection)
            {
                col = (FileCollection) o;
            }
            else
            {
                col = getProject().files(o);
            }

            if (collection == null)
                collection = col;
            else
                collection = collection.plus(col);
        }

        return collection;
    }

    public void addLibs(Object libs)
    {
        this.libs.add(libs);
    }
}

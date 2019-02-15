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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.SequencedInputSupplier;
import net.minecraftforge.gradle.util.SourceDirSetSupplier;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.*;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class ApplyS2STask extends DefaultTask
{
    @InputFiles
    private final List<Object> srg = new LinkedList<Object>();

    @Optional
    @InputFiles
    private final List<Object> exc = new LinkedList<Object>();

    @InputFile
    private Object rangeMap;

    @Optional
    @InputFile
    private Object excModifiers;

    @Input
    private boolean s2sKeepImports = true;

    // stuff defined on the tasks..
    private final List<Object> in = new LinkedList<Object>();
    private Object out;

    @TaskAction
    public void doTask() throws IOException
    {
        File out = getOut();
        File rangemap = getRangeMap();
        File rangelog = File.createTempFile("rangelog", ".txt", this.getTemporaryDir());
        FileCollection srg = getSrgs();
        FileCollection exc = getExcs();

        InputSupplier inSup;

        if (in.size() == 1)
        {
            // just 1 supplier.
            inSup = getInput(in.get(0));
        }
        else
        {
            // multinput
            inSup = new SequencedInputSupplier();
            for (Object o : in)
                ((SequencedInputSupplier) inSup).add(getInput(o));
        }

        OutputSupplier outSup;
        if (in.size() == 1 && in.get(0).equals(out) && in instanceof FolderSupplier)
            outSup = (OutputSupplier) inSup;
        else
            outSup = getOutput(out);

        if (getExcModifiers() != null)
        {
            getLogger().lifecycle("creating default param names");
            exc = generateDefaultExc(getExcModifiers(), exc, srg);
        }

        getLogger().lifecycle("remapping source...");
        applyRangeMap(inSup, outSup, srg, exc, rangemap, rangelog);


        inSup.close();
        outSup.close();
    }

    private InputSupplier getInput(Object o) throws IOException
    {
        if (o instanceof SourceDirectorySet)
        {
            return new SourceDirSetSupplier((SourceDirectorySet) o);
        }

        File f = getProject().file(o);

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

    private OutputSupplier getOutput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            return new ZipOutputSupplier(f);
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
    }

    private void applyRangeMap(InputSupplier inSup, OutputSupplier outSup, FileCollection srg, FileCollection exc, File rangeMap, File rangeLog) throws IOException
    {
        RangeApplier app = new RangeApplier().readSrg(srg.getFiles());

        app.setOutLogger(Constants.getTaskLogStream(getProject(), this.getName() + ".log"));

        app.setKeepImports(this.isS2sKeepImports());

        if (!exc.isEmpty())
        {
            app.readParamMap(exc);
        }

        // for debugging.
        app.dumpRenameMap();

        app.remapSources(inSup, outSup, rangeMap, false);
    }


    private FileCollection generateDefaultExc(File modifiers, FileCollection currentExcs, FileCollection srgs)
    {
        if (modifiers == null || !modifiers.exists())
            return currentExcs;

        Map<String, Boolean> statics = Maps.newHashMap();

        try
        {
            getLogger().debug("  Reading Modifiers:");
            for (String line : Files.readLines(modifiers, Charset.defaultCharset()))
            {
                if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                    continue;
                String[] args = line.split("=");
                statics.put(args[0], "static".equals(args[1]));
            }

            File temp = new File(this.getTemporaryDir(), "generated.exc");
            if (temp.exists())
                temp.delete();

            temp.getParentFile().mkdirs();
            temp.createNewFile();

            BufferedWriter writer = Files.newWriter(temp, Charsets.UTF_8);
            for (File f : srgs)
            {
                getLogger().debug("  Reading SRG: " + f);
                for (String line : Files.readLines(f, Charset.defaultCharset()))
                {
                    if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                        continue;

                    String type = line.substring(0, 2);
                    line = line.substring(4);
                    String[] pts = line.split(" ");

                    if (type.equals("MD"))
                    {
                        String name = pts[2].substring(pts[2].lastIndexOf('/') + 1);
                        if (name.startsWith("func_"))
                        {
                            Boolean isStatic = statics.get(pts[0] + pts[1]);
                            getLogger().debug("    MD: " + line);
                            name = name.substring(5, name.indexOf('_', 5));

                            List<String> params = Lists.newArrayList();
                            int idx = isStatic == null || !isStatic.booleanValue() ? 1 : 0;
                            getLogger().debug("      Name: " + name + " Idx: " + idx);

                            int i = 0;
                            boolean inArray = false;
                            while (i < pts[1].length())
                            {
                                char c = pts[1].charAt(i);

                                switch (c)
                                {
                                    case '(': //Start
                                        break;
                                    case ')': //End
                                        i = pts[1].length();
                                        break;
                                    case '[': //Array
                                        inArray = true;
                                        break;
                                    case 'L': //Class
                                        String right = pts[1].substring(i);
                                        String className = right.substring(1, right.indexOf(';'));
                                        i += className.length() + 1;
                                        params.add("p_" + name + "_" + idx++ + "_");
                                        inArray = false;
                                        break;
                                    case 'B':
                                    case 'C':
                                    case 'D':
                                    case 'F':
                                    case 'I':
                                    case 'J':
                                    case 'S':
                                    case 'Z':
                                        params.add("p_" + name + "_" + idx++ + "_");
                                        if ((c == 'D' || c == 'J') && !inArray) idx++;
                                        inArray = false;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Unrecognized type in method descriptor: " + c);
                                }
                                i++;
                            }

                            if (params.size() > 0)
                            {
                                writer.write(pts[2].substring(0, pts[2].lastIndexOf('/')));
                                writer.write('.');
                                writer.write(pts[2].substring(pts[2].lastIndexOf('/') + 1));
                                writer.write(pts[3]);
                                writer.write("=|");
                                writer.write(Joiner.on(',').join(params));
                                writer.newLine();
                            }
                        }
                    }
                }
            }
            writer.close();

            List<File> files = Lists.newArrayList();
            files.add(temp);//Make sure the new one is first to allow others to override
            for (File f : currentExcs)
                files.add(f);

            return getProject().files(files.toArray());
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @InputFiles @SkipWhenEmpty
    public FileCollection getSources()
    {
        FileCollection collection = null;

        for (Object o: this.in)
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
        this.in.add(in);
    }

    @OutputFiles @Optional
    public FileCollection getOuts()
    {
        File outFile = getOut();
        if (outFile.isDirectory())
            return getProject().fileTree(outFile);
        else
            return getProject().files(outFile);
    }

    public File getOut()
    {
        return getProject().file(out);
    }

    public void setOut(Object out)
    {
        this.out = out;
    }

    public FileCollection getSrgs()
    {
        return getProject().files(srg);
    }

    public void addSrg(Object srg)
    {
        this.srg.add(srg);
    }

    public void addSrg(String srg)
    {
        this.srg.add(srg);
    }

    public void addSrg(File srg)
    {
        this.srg.add(srg);
    }

    public FileCollection getExcs()
    {
        return getProject().files(exc);
    }

    public void addExc(Object exc)
    {
        this.exc.add(exc);
    }

    public void addExc(String exc)
    {
        this.exc.add(exc);
    }

    public void addExc(File exc)
    {
        this.exc.add(exc);
    }

    public File getRangeMap()
    {
        return getProject().file(rangeMap);
    }

    public void setRangeMap(Object rangeMap)
    {
        this.rangeMap = rangeMap;
    }

    public void setExcModifiers(Object value)
    {
        this.excModifiers = value;
    }

    public File getExcModifiers()
    {
        return this.excModifiers == null ? null : this.getProject().file(excModifiers);
    }

    public boolean isS2sKeepImports()
    {
        return this.s2sKeepImports;
    }

    public void setS2sKeepImports(boolean value)
    {
        this.s2sKeepImports = value;
    }
}

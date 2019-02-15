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
package net.minecraftforge.gradle.user;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternSet;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class TaskSourceCopy extends DefaultTask
{
    @InputFiles
    SourceDirectorySet      source;

    @Input
    HashMap<String, Object> replacements = new HashMap<String, Object>();

    @Input
    ArrayList<String>       includes     = new ArrayList<String>();

    @OutputDirectory
    Object             output;

    @SuppressWarnings("unchecked")
    @TaskAction
    public void doTask() throws IOException
    {
        // get the include/exclude patterns from the source (this is different than what's returned by getFilter)
        PatternSet patterns = new PatternSet();
        patterns.setIncludes(source.getIncludes());
        patterns.setExcludes(source.getExcludes());

        // get output
        File out = getOutput();
        if (out.exists())
            deleteDir(out);

        out.mkdirs();
        out = out.getCanonicalFile();

        // resolve replacements
        HashMap<String, String> repl = new HashMap<String, String>(replacements.size());
        for (Entry<String, Object> e : replacements.entrySet())
        {
            if (e.getKey() == null || e.getValue() == null)
                continue; // we dont deal with nulls.
            
            Object val = e.getValue();
            while (val instanceof Closure)
                val = ((Closure<Object>) val).call();

            repl.put(Pattern.quote(e.getKey()), val.toString());
        }

        getLogger().debug("REPLACE >> " + repl);

        // start traversing tree
        for (DirectoryTree dirTree : source.getSrcDirTrees())
        {
            File dir = dirTree.getDir();
            getLogger().debug("PARSING DIR >> " + dir);

            // handle nonexistant srcDirs
            if (!dir.exists() || !dir.isDirectory())
                continue;
            else
                dir = dir.getCanonicalFile();

            // this could be written as .matching(source), but it doesn't actually work
            // because later on gradle casts it directly to PatternSet and crashes
            FileTree tree = getProject().fileTree(dir).matching(source.getFilter()).matching(patterns);

            for (File file : tree)
            {
                File dest = getDest(file, dir, out);
                dest.getParentFile().mkdirs();
                dest.createNewFile();

                if (isIncluded(file))
                {
                    getLogger().debug("PARSING FILE IN >> " + file);
                    String text = Files.toString(file, Charsets.UTF_8);

                    for (Entry<String, String> entry : repl.entrySet())
                        text = text.replaceAll(entry.getKey(), entry.getValue());

                    getLogger().debug("PARSING FILE OUT >> " + dest);
                    Files.write(text, dest, Charsets.UTF_8);
                }
                else
                {
                    Files.copy(file, dest);
                }
            }
        }
    }

    private File getDest(File in, File base, File baseOut) throws IOException
    {
        String relative = in.getCanonicalPath().replace(base.getCanonicalPath(), "");
        return new File(baseOut, relative);
    }

    private boolean isIncluded(File file) throws IOException
    {
        if (includes.isEmpty())
            return true;

        String path = file.getCanonicalPath().replace('\\', '/');
        for (String include : includes)
        {
            if (path.endsWith(include.replace('\\', '/')))
                return true;
        }

        return false;
    }

    private boolean deleteDir(File dir)
    {
        if (dir.exists())
        {
            File[] files = dir.listFiles();
            if (null != files)
            {
                for (int i = 0; i < files.length; i++)
                {
                    if (files[i].isDirectory())
                    {
                        deleteDir(files[i]);
                    }
                    else
                    {
                        files[i].delete();
                    }
                }
            }
        }
        return (dir.delete());
    }

    public File getOutput()
    {
        return getProject().file(output);
    }

    public void setOutput(Object output)
    {
        this.output = output;
    }

    public void setSource(SourceDirectorySet source)
    {
        this.source = source;
    }

    public FileCollection getSource()
    {
        return source;
    }

    public void replace(String key, Object val)
    {
        replacements.put(key, val);
    }

    public void replace(Map<String, Object> map)
    {
        replacements.putAll(map);
    }

    public HashMap<String, Object> getReplacements()
    {
        return replacements;
    }

    public void include(String str)
    {
        includes.add(str);
    }

    public void include(List<String> strs)
    {
        includes.addAll(strs);
    }

    public ArrayList<String> getIncudes()
    {
        return includes;
    }
}

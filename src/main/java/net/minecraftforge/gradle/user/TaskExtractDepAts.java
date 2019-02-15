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

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class TaskExtractDepAts extends DefaultTask
{
    @Input
    private List<String> configurations = Lists.newArrayList();
    @OutputDirectory
    private Object               outputDir;

    @TaskAction
    public void doTask() throws IOException
    {
        FileCollection col = getCollections();
        File outputDir = getOutputDir();
        outputDir.mkdirs(); // make sur eit exists
        
        // make a list of things to delete...
        List<File> toDelete = Lists.newArrayList(outputDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f)
            {
                return f.isFile();
            }
        }));

        Splitter splitter = Splitter.on(' ');

        for (File f : col)
        {
            if (!f.exists() || !f.getName().endsWith("jar"))
                continue;

            try (JarFile jar = new JarFile(f))
            {
                Manifest man = jar.getManifest();

                if (man != null)
                {
                    String atString = man.getMainAttributes().getValue("FMLAT");
                    if (!Strings.isNullOrEmpty(atString))
                    {
                        for (String at : splitter.split(atString.trim()))
                        {
                            // append _at.cfg just in case its not there already...
                            // also differentiate the file name, in cas the same At comes from multiple jars.. who knows why...
                            File outFile = new File(outputDir, at + "_" + Files.getNameWithoutExtension(f.getName()) + "_at.cfg");
                            toDelete.remove(outFile);

                            JarEntry entry = jar.getJarEntry("META-INF/" + at);


                            try (InputStream istream = jar.getInputStream(entry);
                                 OutputStream ostream = new FileOutputStream(outFile))
                            {
                                ByteStreams.copy(istream, ostream);
                            }
                        }
                    }
                }
            }
        }
        
        // remove the files that shouldnt be there...
        for (File f : toDelete)
        {
            f.delete();
        }
    }

    public FileCollection getCollections()
    {
    	List<Configuration> configs = Lists.newArrayListWithCapacity(configurations.size());
    	for (String s : configurations)
    		configs.add(getProject().getConfigurations().getByName(s));
        return getProject().files(configs);
    }

    public void addCollection(String col)
    {
        configurations.add(col);
    }

    public File getOutputDir()
    {
        return getProject().file(outputDir);
    }

    public void setOutputDir(Object outputFile)
    {
        this.outputDir = outputFile;
    }
}

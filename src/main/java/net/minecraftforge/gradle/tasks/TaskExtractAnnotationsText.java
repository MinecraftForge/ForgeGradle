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
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.util.AnnotationUtils;
import net.minecraftforge.gradle.util.AnnotationUtils.ASMInfo;
import net.minecraftforge.gradle.util.AnnotationUtils.Annotation;

public class TaskExtractAnnotationsText extends DefaultTask
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @InputFile
    private Object jar;
    @OutputFile
    private Object output;

    public File getJar()
    {
        return getProject().file(jar);
    }

    public void setJar(Object jar)
    {
        this.jar = jar;
    }

    public File getOutput()
    {
        return getProject().file(output);
    }

    public void setOutput(Object output)
    {
        this.output = output;
    }

    public TaskExtractAnnotationsText()
    {
        //this.getOutputs().upToDateWhen(Constants.CALL_FALSE);
    }

    @TaskAction
    public void doTask() throws IOException
    {
        File input = getJar();

        Map<String, ASMInfo> asm_info = Maps.newTreeMap(); //Tree map because I like sorted outputs.
        //Map<String, Integer> class_versions = Maps.newTreeMap();

        try (ZipFile in = new ZipFile(input))
        {
            for (ZipEntry e : Collections.list(in.entries()))
            {
                if (e.isDirectory())
                    continue;

                // correct source name
                if (e.getName().endsWith(".class"))
                {
                  if (e.getName().endsWith("$.class")) //Scala synthetic class, skip
                    continue;
                    byte[] data = ByteStreams.toByteArray(in.getInputStream(e));
                    ASMInfo info = AnnotationUtils.processClass(data);
                    if (info != null)
                    {
                        String name = e.getName().substring(0, e.getName().length() - 6);
                        //class_versions.put(name, info.version);
                        info.version = null;
                        if (info.annotations != null)
                        {
                            for (Annotation anno : info.annotations)
                            {
                                if (anno.values != null && anno.values.size() == 1 && anno.values.containsKey("value"))
                                {
                                    anno.value = anno.values.get("value");
                                    anno.values = null;
                                }
                            }
                        }
                        if (info.annotations != null || info.interfaces != null)
                            asm_info.put(name, info);
                    }
                }
            }

            Files.write(GSON.toJson(asm_info), getOutput(), Charsets.UTF_8);
        }
    }
}

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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.AnnotationUtils;
import net.minecraftforge.gradle.util.AnnotationUtils.ASMInfo;
import net.minecraftforge.gradle.util.AnnotationUtils.Annotation;

public class TaskExtractAnnotations extends DefaultTask
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Object jar;

    public TaskExtractAnnotations()
    {
        this.getOutputs().upToDateWhen(Constants.CALL_FALSE);
    }

    @TaskAction
    public void doTask() /*throws IOException*/
    {
        try { //Temporary for now, so we dont break people's builds... at least... we shouldn't.
        File out = getJar();
        File tempIn = File.createTempFile("input", ".jar", getTemporaryDir());
        File tempOut = File.createTempFile("output", ".jar", getTemporaryDir());
        tempIn.deleteOnExit();
        tempOut.deleteOnExit();

        Constants.copyFile(out, tempIn); // copy the to-be-output jar to the temporary input location. because output == input

        processJar(tempIn, tempOut);

        Constants.copyFile(tempOut, out);// This is the only 'destructive' line, IF we do error on here. then something is screwy... If we error above then it'd be just like this never run.
        tempOut.delete();
        } catch (IOException e) {
            this.getProject().getLogger().error("Error while building FML annotations cache: " + e.getMessage(), e);
        }
    }

    private void processJar(File input, File output) throws IOException
    {
        Map<String, ASMInfo> asm_info = Maps.newTreeMap(); //Tree map because I like sorted outputs.
        Map<String, Integer> class_versions = Maps.newTreeMap();

        try (ZipFile in = new ZipFile(input);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output))))
        {
            for (ZipEntry e : Collections.list(in.entries()))
            {
                if (e.isDirectory())
                {
                    out.putNextEntry(e);
                    continue;
                }

                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);

                byte[] data = ByteStreams.toByteArray(in.getInputStream(e));
                out.write(data);

                // correct source name
                if (e.getName().endsWith(".class"))
                {
                    ASMInfo info = AnnotationUtils.processClass(data);
                    if (info != null)
                    {
                        String name = e.getName().substring(0, e.getName().length() - 6);
                        class_versions.put(name, info.version);
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

            if (!asm_info.isEmpty())
            {
                String data = GSON.toJson(asm_info);
                ZipEntry cache = new ZipEntry("META-INF/fml_cache_annotation.json");
                cache.setTime(new Date().getTime());
                out.putNextEntry(cache);
                out.write(data.getBytes(Charsets.UTF_8));

                data = GSON.toJson(class_versions);
                cache = new ZipEntry("META-INF/fml_cache_class_versions.json");
                cache.setTime(new Date().getTime());
                out.putNextEntry(cache);
                out.write(data.getBytes(Charsets.UTF_8));
            }
        }
    }

    public File getJar()
    {
        return getProject().file(jar);
    }

    public void setJar(Object jar)
    {
        this.jar = jar;
    }
}

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
package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public class TaskSingleDeobfBin extends CachedTask
{
    @InputFile
    private Object methodCsv;

    @InputFile
    private Object fieldCsv;

    @InputFile
    private Object inJar;

    @Cached
    @OutputFile
    private Object outJar;

    @TaskAction
    public void doTask() throws IOException
    {
        final Map<String, String> methods = Maps.newHashMap();
        final Map<String, String> fields = Maps.newHashMap();

        // read CSV files
        CSVReader reader = Constants.getReader(getMethodCsv());
        for (String[] s : reader.readAll())
        {
            methods.put(s[0], s[1]);
        }

        reader = Constants.getReader(getFieldCsv());
        for (String[] s : reader.readAll())
        {
            fields.put(s[0], s[1]);
        }

        // actually do the jar copy..
        File input = getInJar();
        File output = getOutJar();

        output.getParentFile().mkdirs();

        // begin reading jar
        ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
        JarOutputStream zout = new JarOutputStream(new FileOutputStream(output));
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {
            if (entry.getName().contains("META-INF"))
            {
                // Skip signature files
                if (entry.getName().endsWith(".SF") || entry.getName().endsWith(".DSA"))
                {
                    continue;
                }
                // Strip out file signatures from manifest
                else if (entry.getName().equals("META-INF/MANIFEST.MF"))
                {
                    Manifest mf = new Manifest(zin);
                    mf.getEntries().clear();
                    zout.putNextEntry(new JarEntry(entry.getName()));
                    mf.write(zout);
                    zout.closeEntry();
                    continue;
                }
            }

            // resources or directories.
            if (entry.isDirectory() || !entry.getName().endsWith(".class"))
            {
                zout.putNextEntry(new JarEntry(entry));
                ByteStreams.copy(zin, zout);
                zout.closeEntry();
            }
            else
            {
                // classes
                zout.putNextEntry(new JarEntry(entry.getName()));
                zout.write(deobfClass(ByteStreams.toByteArray(zin), methods, fields));
                zout.closeEntry();
            }
        }

        zout.close();
        zin.close();
    }

    private static byte[] deobfClass(byte[] classData, final Map<String, String> methods, final Map<String, String> fields)
    {
        ClassReader reader = new ClassReader(classData);
        ClassWriter writer = new ClassWriter(0);
        Remapper remapper = new Remapper() {
            @Override
            public String mapFieldName(final String owner, final String name, final String desc)
            {
                String mappedName = fields.get(name);
                return mappedName != null ? mappedName : name;
            }

            @Override
            public String mapMethodName(final String owner, final String name, final String desc)
            {
                String mappedName = methods.get(name);
                return mappedName != null ? mappedName : name;
            }

            @Override
            public String mapInvokeDynamicMethodName(final String name, final String desc)
            {
                String mappedName = methods.get(name);
                return mappedName != null ? mappedName : name;
            }
        };
        ClassRemapper adapter = new ClassRemapper(writer, remapper);
        reader.accept(adapter, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    public File getMethodCsv()
    {
        return getProject().file(methodCsv);
    }

    public void setMethodCsv(Object methodCsv)
    {
        this.methodCsv = methodCsv;
    }

    public File getFieldCsv()
    {
        return getProject().file(fieldCsv);
    }

    public void setFieldCsv(Object fieldCsv)
    {
        this.fieldCsv = fieldCsv;
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }
}

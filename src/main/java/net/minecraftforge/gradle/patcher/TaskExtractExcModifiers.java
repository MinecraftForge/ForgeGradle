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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

class TaskExtractExcModifiers extends DefaultTask
{
    @InputFile
    private Object inJar;
    
    @OutputFile
    private Object outExc;
    
    //@formatter:off
    public TaskExtractExcModifiers() { super(); }
    //@formatter:on
    
    @TaskAction
    public void doStuff() throws IOException
    {
        File output = getOutExc();
        File input = getInJar();

        if (output.exists())
            output.delete();

        output.getParentFile().mkdirs();
        output.createNewFile();
        
        
        BufferedWriter writer = Files.newWriter(output, Charsets.UTF_8);
        ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {
            if (entry.isDirectory())
                continue;

            String entryName = entry.getName();
            
            if (!entryName.endsWith(".class") || !entryName.startsWith("net/minecraft/"))
                continue;

            getProject().getLogger().debug("Processing " + entryName);
            byte[] entryData = ByteStreams.toByteArray(zin);

            ClassReader cr = new ClassReader(entryData);
            ClassVisitor ca = new GenerateMapClassAdapter(writer);
            cr.accept(ca, 0);
        }

        zin.close();
        writer.close();
    }
    
    private static class GenerateMapClassAdapter extends ClassVisitor
    {
        String className;
        BufferedWriter writer;

        public GenerateMapClassAdapter(BufferedWriter writer)
        {
            super(Opcodes.ASM5);
            this.writer = writer;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
        {
            this.className = name;
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
        {
            if (name.equals("<clinit>"))
                return super.visitMethod(access, name, desc, signature, exceptions);

            String clsSig = this.className + "/" + name + desc;

            try
            {
                if ((access & Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC)
                {
                    writer.write(clsSig);
                    writer.write("=static");
                    writer.newLine();
                }
            }
            catch (IOException e)
            {
                Throwables.propagate(e);
            }
            return super.visitMethod(access, name, desc, signature, exceptions);
        }
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutExc()
    {
        return getProject().file(outExc);
    }

    public void setOutExc(Object outExc)
    {
        this.outExc = outExc;
    }
}

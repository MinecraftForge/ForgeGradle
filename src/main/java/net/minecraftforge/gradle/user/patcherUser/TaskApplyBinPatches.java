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
package net.minecraftforge.gradle.user.patcherUser;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.regex.Pattern;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.nothome.delta.GDiffPatcher;

import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

public class TaskApplyBinPatches extends CachedTask
{
    //@formatter:off
    @InputFile  Object inJar;
    @InputFile  Object classesJar;
    @InputFile  Object resourcesJar;
    @InputFile  Object patches;
    //@formatter:on

    @OutputFile
    @Cached
    Object                              outJar;

    private HashMap<String, ClassPatch> patchlist = Maps.newHashMap();
    private GDiffPatcher                patcher   = new GDiffPatcher();

    @TaskAction
    public void doTask() throws IOException
    {
        setup();

        if (getOutJar().exists())
        {
            getOutJar().delete();
        }

        final HashSet<String> entries = new HashSet<String>();

        try (ZipFile in = new ZipFile(getInJar());
             ZipInputStream classesIn = new ZipInputStream(new FileInputStream(getClassJar()));
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(getOutJar()))))
        {
            // DO PATCHES
            log("Patching Class:");
            for (ZipEntry e : Collections.list(in.entries()))
            {
                if (e.getName().contains("META-INF"))
                    continue;

                if (e.isDirectory())
                {
                    out.putNextEntry(e);
                }
                else
                {
                    ZipEntry n = new ZipEntry(e.getName());
                    n.setTime(e.getTime());
                    out.putNextEntry(n);

                    byte[] data = ByteStreams.toByteArray(in.getInputStream(e));
                    ClassPatch patch = patchlist.get(e.getName().replace('\\', '/'));

                    if (patch != null)
                    {
                        log("\t%s (%s) (input size %d)", patch.targetClassName, patch.sourceClassName, data.length);
                        int inputChecksum = adlerHash(data);
                        if (patch.inputChecksum != inputChecksum)
                        {
                            throw new RuntimeException(String.format("There is a binary discrepency between the expected input class %s (%s) and the actual class. Checksum on disk is %x, in patch %x. Things are probably about to go very wrong. Did you put something into the jar file?", patch.targetClassName, patch.sourceClassName, inputChecksum, patch.inputChecksum));
                        }
                        synchronized (patcher)
                        {
                            data = patcher.patch(data, patch.patch);
                        }
                    }

                    out.write(data);
                }

                // add the names to the hashset
                entries.add(e.getName());
            }

            // COPY DATA
            ZipEntry entry = null;
            while ((entry = classesIn.getNextEntry()) != null)
            {
                if (entries.contains(entry.getName()))
                    continue;

                out.putNextEntry(entry);
                out.write(ByteStreams.toByteArray(classesIn));
                entries.add(entry.getName());
            }

            getProject().zipTree(getResourceJar()).visit(new FileVisitor()
            {
                @Override
                public void visitDir(FileVisitDetails dirDetails)
                {
                }

                @Override
                public void visitFile(FileVisitDetails file)
                {
                    try
                    {
                        String name = file.getRelativePath().toString().replace('\\', '/');
                        if (!entries.contains(name))
                        {
                            ZipEntry n = new ZipEntry(name);
                            n.setTime(file.getLastModified());
                            out.putNextEntry(n);
                            ByteStreams.copy(file.open(), out);
                            entries.add(name);
                        }
                    }
                    catch (IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }

            });
        }
    }

    private int adlerHash(byte[] input)
    {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return (int) hasher.getValue();
    }

    public void setup()
    {
        Pattern matcher = Pattern.compile("binpatch/merged/.*.binpatch");

        byte[] bytes;
        try (ByteArrayOutputStream jarBytes = new ByteArrayOutputStream())
        {
            try (LzmaInputStream binpatchesDecompressed = new LzmaInputStream(new FileInputStream(getPatches()), new Decoder());
                 JarOutputStream jos = new JarOutputStream(jarBytes))
            {
                Pack200.newUnpacker().unpack(binpatchesDecompressed, jos);
            }
            bytes = jarBytes.toByteArray();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        log("Reading Patches:");
        try (JarInputStream jis = new JarInputStream(new ByteArrayInputStream(bytes)))
        {
            do
            {
                JarEntry entry = jis.getNextJarEntry();
                if (entry == null)
                {
                    break;
                }

                if (matcher.matcher(entry.getName()).matches())
                {
                    ClassPatch cp = readPatch(entry, jis);
                    patchlist.put(cp.sourceClassName.replace('.', '/') + ".class", cp);
                }
                jis.closeEntry();
            } while (true);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        log("Read %d binary patches", patchlist.size());
        log("Patch list :\n\t%s", Joiner.on("\n\t").join(patchlist.entrySet()));
    }

    private ClassPatch readPatch(JarEntry patchEntry, JarInputStream jis) throws IOException
    {
        log("\t%s", patchEntry.getName());
        ByteArrayDataInput input = ByteStreams.newDataInput(ByteStreams.toByteArray(jis));

        String name = input.readUTF();
        String sourceClassName = input.readUTF();
        String targetClassName = input.readUTF();
        boolean exists = input.readBoolean();
        int inputChecksum = 0;
        if (exists)
        {
            inputChecksum = input.readInt();
        }
        int patchLength = input.readInt();
        byte[] patchBytes = new byte[patchLength];
        input.readFully(patchBytes);

        return new ClassPatch(name, sourceClassName, targetClassName, exists, inputChecksum, patchBytes);
    }

    private void log(String format, Object... args)
    {
        getLogger().debug(String.format(format, args));
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

    public File getPatches()
    {
        return getProject().file(patches);
    }

    public void setPatches(Object patchesJar)
    {
        this.patches = patchesJar;
    }

    public File getClassJar()
    {
        return getProject().file(classesJar);
    }

    public void setClassJar(Object extraJar)
    {
        this.classesJar = extraJar;
    }

    public File getResourceJar()
    {
        return getProject().file(resourcesJar);
    }

    public void setResourceJar(Object resources)
    {
        this.resourcesJar = resources;
    }

    public static class ClassPatch
    {
        public final String  name;
        public final String  sourceClassName;
        public final String  targetClassName;
        public final boolean existsAtTarget;
        public final byte[]  patch;
        public final int     inputChecksum;

        public ClassPatch(String name, String sourceClassName, String targetClassName, boolean existsAtTarget, int inputChecksum, byte[] patch)
        {
            this.name = name;
            this.sourceClassName = sourceClassName;
            this.targetClassName = targetClassName;
            this.existsAtTarget = existsAtTarget;
            this.inputChecksum = inputChecksum;
            this.patch = patch;
        }

        @Override
        public String toString()
        {
            return String.format("%s : %s => %s (%b) size %d", name, sourceClassName, targetClassName, existsAtTarget, patch.length);
        }
    }
}

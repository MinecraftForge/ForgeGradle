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

import com.google.common.io.ByteStreams;
import com.nothome.delta.Delta;
import lzma.streams.LzmaOutputStream;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.util.patching.BinPatches;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class TaskApplyBinPatchesTest extends TaskTest<TaskApplyBinPatches>
{
    private static final String BEFORE_PATH = "/net/minecraftforge/gradle/user/patcherUser/patchBefore.txt";
    private static final String AFTER_PATH = "/net/minecraftforge/gradle/user/patcherUser/patchAfter.txt";
    private static final String PATCHED_FILE_NAME_IN_JAR = "test";
    private static final String PATCHED_FILE_NAME_IN_JAR_WITH_EXTENSION = "test.class";
    private static final String[] DIRECTORY_NAMES_IN_JAR = new String[]{"DIR1/", "DIR2/", "DIR3/"};
    private static final String[] FILE_NAMES_IN_JAR = new String[]{"DIR1/inDir1.txt", "DIR2/inDir2.txt", "inRoot.txt"};

    private void createPatch(InputStream from, InputStream to, OutputStream patch) throws IOException
    {
        byte[] fromBytes = ByteStreams.toByteArray(from);
        byte[] toBytes = ByteStreams.toByteArray(to);
        patch.write(BinPatches.getBinPatchBytesWithHeader(new Delta(), PATCHED_FILE_NAME_IN_JAR, PATCHED_FILE_NAME_IN_JAR, fromBytes, toBytes));
    }

    private void packJar(JarInputStream jarInputStream, OutputStream outputStream) throws IOException
    {
        try (LzmaOutputStream lzmaOutputStream = new LzmaOutputStream.Builder(outputStream).useEndMarkerMode(true).build())
        {
            Pack200.newPacker().pack(jarInputStream, lzmaOutputStream);
        }
    }

    @Test
    public void runTask() throws Exception
    {
        File inputJar = temporaryFolder.newFile("before.jar");
        File outputJar = temporaryFolder.newFile("after.jar");
        File patchJar = temporaryFolder.newFile("patches.jar.pack200.lzma");
        File classJar = temporaryFolder.newFile("empty.jar");
        File resourceJar = classJar;


        byte[] uncompressedPatchJar;

        {
            // TODO: we used this wrong elsewhere
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (JarOutputStream patchOutputStream = new JarOutputStream(new BufferedOutputStream(byteArrayOutputStream));
                 InputStream beforeInputStream = getClass().getResourceAsStream(BEFORE_PATH);
                 InputStream afterInputStream = getClass().getResourceAsStream(AFTER_PATH))
            {
                patchOutputStream.putNextEntry(new ZipEntry("binpatch/merged/" + PATCHED_FILE_NAME_IN_JAR + " .binpatch"));
                createPatch(beforeInputStream, afterInputStream, patchOutputStream);
            }
            uncompressedPatchJar = byteArrayOutputStream.toByteArray();
        }

        try (OutputStream patchOutputStream = new BufferedOutputStream(new FileOutputStream(patchJar)))
        {
            packJar(new JarInputStream(new ByteArrayInputStream(uncompressedPatchJar)), patchOutputStream);
        }

        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(classJar)))
        {
            // TODO: add some test classes/resources?
        }

        try (JarOutputStream inputOutputStream = new JarOutputStream(new BufferedOutputStream(new FileOutputStream(inputJar)));
             InputStream beforeInputStream = getClass().getResourceAsStream(BEFORE_PATH))
        {
            for (String directory : DIRECTORY_NAMES_IN_JAR)
            {
                assert directory.endsWith("/") : "directory name '" + directory + "' must end with /";
                inputOutputStream.putNextEntry(new ZipEntry(directory));
            }
            for (String file : FILE_NAMES_IN_JAR)
            {
                assert !file.endsWith("/") : "file name '" + file + "' must not end with /";
                inputOutputStream.putNextEntry(new ZipEntry(file));
            }

            inputOutputStream.putNextEntry(new ZipEntry(PATCHED_FILE_NAME_IN_JAR_WITH_EXTENSION));
            ByteStreams.copy(beforeInputStream, inputOutputStream);
        }

        TaskApplyBinPatches taskApplyBinPatches = getTask(TaskApplyBinPatches.class);
        taskApplyBinPatches.setInJar(inputJar);
        taskApplyBinPatches.setClassJar(classJar);
        taskApplyBinPatches.setPatches(patchJar);
        taskApplyBinPatches.setOutJar(outputJar);
        taskApplyBinPatches.setResourceJar(resourceJar);
        taskApplyBinPatches.doTask();

        Assert.assertTrue("File '" + outputJar + "' should exist", outputJar.isFile());

        Map<String, byte[]> contents = new HashMap<>();
        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(outputJar)))
        {
            JarEntry e;
            while ((e = jarInputStream.getNextJarEntry()) != null)
            {
                contents.put(e.getName(), ByteStreams.toByteArray(jarInputStream));
            }
        }

        for (String directory : DIRECTORY_NAMES_IN_JAR)
            Assert.assertNotNull("Directory " + directory + " should be in " + outputJar, contents.get(directory));

        for (String file : FILE_NAMES_IN_JAR)
            Assert.assertNotNull("File " + file + " should be in " + outputJar, contents.get(file));

        byte[] expectedBytes;
        try (InputStream inputStream = getClass().getResourceAsStream(AFTER_PATH))
        {
            expectedBytes = ByteStreams.toByteArray(inputStream);
        }
        Assert.assertArrayEquals(expectedBytes, contents.get(PATCHED_FILE_NAME_IN_JAR_WITH_EXTENSION));
    }
}

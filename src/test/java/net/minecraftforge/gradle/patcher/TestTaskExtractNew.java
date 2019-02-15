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
package net.minecraftforge.gradle.patcher;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.jar.*;

public class TestTaskExtractNew extends TaskTest<TaskExtractNew>
{
    private static final String ADDED_FILE = "%d/added_file_%d";
    private static final int ADDED_FILE_COUNT = 10;

    @Test
    public void runTask() throws IOException
    {
        File cleanJar = TestResource.ACTUAL_CLEAN_JAR.getFile(temporaryFolder);
        File dirtyJar = temporaryFolder.newFile("dirty.jar");

        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(cleanJar));
             JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(dirtyJar)))
        {
            JarEntry e;
            while ((e = jarInputStream.getNextJarEntry()) != null)
            {
                jarOutputStream.putNextEntry(e);
                ByteStreams.copy(jarInputStream, jarOutputStream);
            }
            for (int i = 0; i < ADDED_FILE_COUNT; i++)
            {
                jarOutputStream.putNextEntry(new JarEntry(String.format(ADDED_FILE, i, i)));
                jarOutputStream.write(String.valueOf(i).getBytes(Charsets.UTF_8));
            }
        }

        File outJar = temporaryFolder.newFile("output.jar");

        TaskExtractNew taskExtractNew = getTask(TaskExtractNew.class);
        taskExtractNew.addCleanSource(cleanJar);
        taskExtractNew.addDirtySource(dirtyJar);
        taskExtractNew.setOutput(outJar);
        taskExtractNew.doStuff();

        try (JarFile outJarFile = new JarFile(outJar))
        {
            Assert.assertEquals(ADDED_FILE_COUNT, outJarFile.size());

            for (int i = 0; i < ADDED_FILE_COUNT; i++)
            {
                Assert.assertNotNull(outJarFile.getJarEntry(String.format(ADDED_FILE, i, i)));
            }
        }
    }
}

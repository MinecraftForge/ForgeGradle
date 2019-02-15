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

import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.jar.*;
import java.util.zip.*;

public class TestTaskRecompileMc extends TaskTest<TaskRecompileMc>
{
    @Test
    public void runTask() throws IOException
    {
        File sourceJar = TestResource.ORG_EXAMPLE_EXAMPLE_SRC_JAR.getFile(temporaryFolder);
        File resourceJar = TestResource.MERGE_EXPECTED_ZIP.getFile(temporaryFolder);
        File outJar = temporaryFolder.newFile("out.jar");

        TaskRecompileMc task = getTask(TaskRecompileMc.class);
        task.setInSources(sourceJar);
        task.setOutJar(outJar);
        task.getProject().getConfigurations().maybeCreate("test_configuration");
        task.setClasspath("test_configuration");
        task.setInResources(resourceJar);
        task.doStuff();

        try (JarFile outJarFile = new JarFile(outJar);
             JarFile resourceJarFile = new JarFile(resourceJar);
             JarFile sourceJarFile = new JarFile(sourceJar))
        {
            resourceJarFile.stream().forEach(it ->
            {
                ZipEntry afterRecompile = outJarFile.getEntry(it.getName());
                Assert.assertNotNull("Entry for resource " + it.getName() + " should exist", afterRecompile);
                Assert.assertEquals("Entry for resource " + it.getName() + " should have same size", it.getSize(), afterRecompile.getSize());
            });
            sourceJarFile.stream().forEach(it ->
            {
                ZipEntry afterRecompile = outJarFile.getEntry(it.getName().replace(".java", ".class"));
                Assert.assertNotNull("Entry for source " + it.getName() + " should exist", afterRecompile);
                Assert.assertNotEquals("Entry for source " + it.getName() + " should have non-zero size", it.getSize());
            });
            Assert.assertNotEquals("output jar '" + outJarFile + "' should not be empty", 0, outJarFile.size());
        }
    }
}

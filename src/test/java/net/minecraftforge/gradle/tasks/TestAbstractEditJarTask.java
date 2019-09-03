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

import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Assert;
import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

public class TestAbstractEditJarTask extends TaskTest<AbstractEditJarTask>
{
    @Test
    public void testReadJarAndStoreInRam() throws Exception {
        testReadJarAndStoreInRam(TestResource.ACTUAL_CLEAN_JAR);
    }

    private void testReadJarAndStoreInRam(TestResource testResource) throws Exception
    {
        AbstractEditJarTask task = getTask(AbstractEditJarTaskDoNothing.class);
        File cleanJar = testResource.getFile(temporaryFolder);
        Map<String, byte[]> resources = new HashMap<>();
        Map<String, String> sources = new HashMap<>();
        task.readAndStoreJarInRam(cleanJar, sources, resources);

        int expectedSourceCount = 0;
        int expectedResourceCount = 0;
        int expectedDirectoryCount = 0;
        try (JarInputStream jarInputStream = new JarInputStream(testResource.getInputStream())) {
            JarEntry e;
            while ((e = jarInputStream.getNextJarEntry()) != null) {
                String name = e.getName();
                if (e.isDirectory())
                    expectedDirectoryCount++;
                else if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".groovy") || name.endsWith(".scala"))
                    expectedSourceCount++;
                else
                    expectedResourceCount++;
            }
        }

        int directoryCount = 0;
        int resourceCount = 0;
        for (Map.Entry<String, byte[]> entry : resources.entrySet())
        {
            String name = entry.getKey();
            byte[] value = entry.getValue();
            if (name.endsWith("/")) {
                Assert.assertEquals("Directory entry should have not bytes", 0, value.length);
                directoryCount++;
            } else {
                Assert.assertNotEquals("Resource entry should have bytes", 0, value.length);
                resourceCount++;
            }
        }

        int sourceCount = 0;
        for (Map.Entry<String, String> entry : sources.entrySet())
        {
            String name = entry.getKey();
            String source = entry.getValue();
            Assert.assertFalse("Should not have directory in sources", name.endsWith("/"));
            Assert.assertNotEquals("Source entry should have bytes", 0, source.length());
            sourceCount++;
        }

        Assert.assertEquals("Should have no sources", expectedSourceCount, sourceCount);
        Assert.assertEquals("Should have resources", expectedResourceCount, resourceCount);
        Assert.assertEquals("Should have directories", expectedDirectoryCount, directoryCount);
    }

    @Test
    public void runTask() throws Throwable
    {
        AbstractEditJarTask task = getTask(AbstractEditJarTaskDoNothing.class);
        File inJar = TestResource.ACTUAL_CLEAN_JAR.getFile(temporaryFolder);
        File outJar = temporaryFolder.newFile("out.jar");
        task.setInJar(inJar);
        task.setOutJar(outJar);
        task.doTask();
        // we don't change anything so they should have the same contents, except META-INF which is intentionally skipped
        try (JarFile inJarFile = new JarFile(inJar);
             JarFile outJarFile = new JarFile(outJar)) {

            for (ZipEntry e : (Iterable<JarEntry>) inJarFile.stream()::iterator) {
                if (e.getName().startsWith("META-INF/"))
                    continue;
                ZipEntry other = outJarFile.getEntry(e.getName());
                Assert.assertNotNull(other);
                Assert.assertEquals(e.getSize(), other.getSize());
            }
        }
    }

    static class AbstractEditJarTaskDoNothing extends AbstractEditJarTask
    {
        public AbstractEditJarTaskDoNothing()
        {
            super();
        }

        @Override
        public void doStuffBefore() throws Exception
        {

        }

        @Override
        public String asRead(String name, String file) throws Exception
        {
            return file;
        }

        @Override
        public void doStuffMiddle(Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception
        {

        }

        @Override
        public void doStuffAfter() throws Exception
        {

        }

        @Override
        protected boolean storeJarInRam()
        {
            return false;
        }
    }
}

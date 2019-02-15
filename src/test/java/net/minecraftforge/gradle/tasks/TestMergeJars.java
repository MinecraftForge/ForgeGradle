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

import groovy.lang.Closure;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.*;
import java.util.zip.*;

public class TestMergeJars extends TaskTest<MergeJars>
{
    private static Closure<File> fileClosure(File f) {
        return new Closure<File>(null)
        {
            @Override
            public File call()
            {
                return f;
            }
        };
    }

    private static String zipName(Class<?> clazz) {
        return clazz.getName().replace('.', '/') + ".class";
    }

    @Test
    public void runTask() throws IOException
    {
        File a = TestResource.MERGE_A_ZIP.getFile(temporaryFolder);
        File b = TestResource.MERGE_B_ZIP.getFile(temporaryFolder);
        File out = temporaryFolder.newFile("out.jar");
        File expected = TestResource.MERGE_EXPECTED_ZIP.getFile(temporaryFolder);

        MergeJars mergeJars = getTask(MergeJars.class);
        mergeJars.setClient(fileClosure(a));
        mergeJars.setServer(fileClosure(b));
        mergeJars.setOutJar(out);
        mergeJars.doTask();

        try (JarFile expectedJar = new JarFile(expected);
             JarFile outJar = new JarFile(out))
        {
            Set<String> expectedSet = expectedJar.stream().filter(TestMergeJars::isFile).map(ZipEntry::getName).collect(Collectors.toSet());
            // Side/SideOnly should always be in the output jar even if not in either input jar
            expectedSet.add(zipName(Side.class));
            expectedSet.add(zipName(SideOnly.class));
            Set<String> outSet = outJar.stream().filter(TestMergeJars::isFile).map(ZipEntry::getName).collect(Collectors.toSet());
            Assert.assertEquals("Entries in expected merged jar should match output merged jar", expectedSet, outSet);

            // since we're assuming we don't merge directory entries, there should be none at all
            // we should either have all of them or none
            Assert.assertEquals(0, outJar.stream().filter(it -> !isFile(it)).count());
        }
    }

    /**
     * directory entries are not required by the zip spec so it's fine if those aren't matching
     */
    private static boolean isFile(JarEntry jarEntry)
    {
        return !jarEntry.getName().endsWith("/");
    }
}

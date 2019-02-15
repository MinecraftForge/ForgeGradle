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
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.util.jar.*;
import java.util.zip.*;

public class TestTaskDepDummy extends TaskTest<TaskDepDummy>
{
    private static final String DUMMY_ENTRY = "dummyThing";

    @Test
    public void runTask() throws IOException
    {
        File dummy = temporaryFolder.newFile("dummy.jar");
        TaskDepDummy taskDepDummy = getTask(TaskDepDummy.class);
        taskDepDummy.setOutputFile(dummy);
        taskDepDummy.makeEmptyJar();

        try (JarFile dummyJar = new JarFile(dummy)) {
            ZipEntry entry = dummyJar.getEntry(DUMMY_ENTRY);
            Assert.assertNotNull("Dummy jar should have entry " + DUMMY_ENTRY, entry);
            Assert.assertEquals("Dummy jar entry should have 1 byte length", 1, entry.getSize());
        }
    }
}

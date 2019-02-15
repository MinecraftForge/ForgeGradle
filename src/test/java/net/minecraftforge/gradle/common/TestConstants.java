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
package net.minecraftforge.gradle.common;

import net.minecraftforge.gradle.testsupport.UsesTemporaryFiles;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * @see Constants
 */
public class TestConstants implements UsesTemporaryFiles
{
    private static String STRING_TO_HASH = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static String EXPECTED_STRING_HASH = "437bba8e0bf58337674f4539e75186ac";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testHashZipWithNoContents() throws Exception
    {
        File zipFile = temporaryFolder.newFile("zipFile.zip");
        try (JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(zipFile)))
        {
            ZipEntry entry = new ZipEntry(STRING_TO_HASH);
            jarOutputStream.putNextEntry(entry);
        }

        // when the zip entry has no contents only its name is hashed so these should match
        Assert.assertEquals(EXPECTED_STRING_HASH, Constants.hash(zipFile));
    }

    @Test
    public void testHashString()
    {
        Assert.assertEquals(EXPECTED_STRING_HASH, Constants.hash(STRING_TO_HASH));
    }
}

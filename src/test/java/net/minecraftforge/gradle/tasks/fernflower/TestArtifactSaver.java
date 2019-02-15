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
package net.minecraftforge.gradle.tasks.fernflower;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.jar.*;

public class TestArtifactSaver
{
    private static final String[] FOLDERS = new String[]{"test", "0-1-2", " #  a sd$#"};
    private static final String ARCHIVE = "test.jar";
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();
    File saveFolder;
    ArtifactSaver saver;

    @BeforeClass
    public static void initContext()
    {
        DecompilerContext.initContext(null, new PrintStreamLogger(System.err));
    }

    @Before
    public void before()
    {
        saveFolder = temporaryFolder.getRoot();
        saver = new ArtifactSaver(saveFolder);
    }

    @After
    public void after()
    {
        Assert.assertFalse("All archive streams in saver '" + saver + "' should be closed", saver.areAnyArchiveStreamsOpen());
        saver = null;
        saveFolder = null;
    }

    @Test
    public void testSaveClassEntry() throws IOException
    {
        saveClassEntry("org.example.Example", "package org.example; public class Example { }", null);
        saveClassEntry("org.example.Example", "package org.example; public class Example { }", new Manifest());
        saveClassEntry("Example", "public class Example { }", new Manifest());
    }

    @Test
    public void saveResourceEntry() throws IOException
    {
        copyFirstEntry(TestResource.ACTUAL_CLEAN_JAR);
        copyFirstEntry(TestResource.ACTUAL_OBF_JAR);
        copyFirstEntry(TestResource.MERGE_EXPECTED_ZIP);
    }

    private void saveClassEntry(String className, String contents, Manifest manifest) throws IOException
    {
        String entryName = className.replace('.', '/') + ".java";

        for (String folder : FOLDERS)
            saver.saveFolder(folder);

        for (String folder : FOLDERS)
        {
            saver.createArchive(folder, ARCHIVE, null);
            saver.saveClassEntry(folder, ARCHIVE, className, entryName, contents);
        }

        for (String folder : FOLDERS)
        {
            saver.closeArchive(folder, ARCHIVE);
        }

        for (String folder : FOLDERS)
        {
            File expected = new File(saveFolder, folder + '/' + ARCHIVE);
            try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(expected)))
            {
                Assert.assertEquals(entryName, jarInputStream.getNextEntry().getName());
                try (Reader reader = new InputStreamReader(jarInputStream, Charsets.UTF_8))
                {
                    Assert.assertEquals(contents, CharStreams.toString(reader));
                }
            }
        }
    }

    private void copyFirstEntry(TestResource testResource) throws IOException
    {
        File source = testResource.getFile(temporaryFolder);
        String firstEntryName;
        byte[] contents;
        try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(source)))
        {
            firstEntryName = jarInputStream.getNextEntry().getName();
            contents = ByteStreams.toByteArray(jarInputStream);
        }

        for (String folder : FOLDERS)
            saver.saveFolder(folder);

        for (String folder : FOLDERS)
        {
            saver.createArchive(folder, ARCHIVE, null);
            saver.copyEntry(source.getAbsolutePath().toString(), folder, ARCHIVE, firstEntryName);
        }

        for (String folder : FOLDERS)
        {
            saver.closeArchive(folder, ARCHIVE);
        }

        for (String folder : FOLDERS)
        {
            File expected = new File(saveFolder, folder + '/' + ARCHIVE);
            try (JarInputStream jarInputStream = new JarInputStream(new FileInputStream(expected)))
            {
                String name = jarInputStream.getNextEntry().getName();
                byte[] outContents = ByteStreams.toByteArray(jarInputStream);
                Assert.assertEquals("Entry name should match", firstEntryName, name);
                Assert.assertArrayEquals("Contents of " + name + " should match", contents, outContents);
            }
        }
    }
}

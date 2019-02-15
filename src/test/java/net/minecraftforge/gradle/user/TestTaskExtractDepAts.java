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
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class TestTaskExtractDepAts extends TaskTest<TaskExtractDepAts>
{
    @Test
    public void runTask() throws IOException
    {
        File outputDir = temporaryFolder.newFolder("extractedAts");
        File shouldBeDeleted = new File(outputDir, "should_be_deleted_at.cfg");
        if (!shouldBeDeleted.createNewFile())
            throw new IOException("Couldn't create file in " + outputDir);
        File modWithAtJar = TestResource.MOD_WITH_AT.getFile(temporaryFolder);
        TaskExtractDepAts task = getTask(TaskExtractDepAts.class);
        task.getProject().getConfigurations().maybeCreate("test_at").getDependencies().add(new DefaultSelfResolvingDependency(new SimpleFileCollection(modWithAtJar)));
        task.addCollection("test_at");
        task.setOutputDir(outputDir);
        task.doTask();
        File[] files = outputDir.listFiles();
        Assert.assertFalse(shouldBeDeleted + " should be deleted", shouldBeDeleted.exists());
        Assert.assertNotNull(files);
        Assert.assertEquals(1, files.length);

        String name = files[0].getName();
        Assert.assertTrue("Name " + name + " should contain test_at", name.contains("test_at"));
    }
}

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

import net.minecraftforge.gradle.testsupport.JarComparison;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Test;

import java.io.*;

public class TestTaskSingleDeobfBin extends TaskTest<TaskSingleDeobfBin>
{
    @Test
    public void runTask() throws IOException
    {
        File outJar = temporaryFolder.newFile("out.jar");

        TaskSingleDeobfBin task = getTask(TaskSingleDeobfBin.class);
        task.setMethodCsv(TestResource.METHODS_CSV.getFile(temporaryFolder));
        task.setFieldCsv(TestResource.FIELDS_CSV.getFile(temporaryFolder));
        task.setInJar(TestResource.ACTUAL_OBF_JAR.getFile(temporaryFolder));
        task.setOutJar(outJar);
        task.doTask();

        // this task applies CSV mappings
        JarComparison.compareJarClassMembers(TestResource.ACTUAL_OBF_CSV_JAR.getFile(temporaryFolder), outJar);
    }
}

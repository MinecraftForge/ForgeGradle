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
import com.google.common.io.Files;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;

public class TestTaskExtractExcModifiers extends TaskTest<TaskExtractExcModifiers>
{
    @Test
    public void runTask() throws Exception
    {
        File inputJar = TestResource.ACTUAL_CLEAN_JAR.getFile(temporaryFolder);
        File outExc = temporaryFolder.newFile("output.exc");

        TaskExtractExcModifiers taskExtractExcModifiers = getTask(TaskExtractExcModifiers.class);
        taskExtractExcModifiers.setInJar(inputJar);
        taskExtractExcModifiers.setOutExc(outExc);
        taskExtractExcModifiers.matchingPrefix = "test/";
        taskExtractExcModifiers.doStuff();

        String contents = Files.toString(outExc, Charsets.UTF_8).replace("\r", "");
        Assert.assertEquals("test/actual/NonDepUser/doSuff()V=static\n" +
                "test/actual/NonDepUser/doStuffObf()V=static\n", contents);

    }
}

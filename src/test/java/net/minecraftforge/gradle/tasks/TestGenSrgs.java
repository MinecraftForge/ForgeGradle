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

import com.google.common.base.Charsets;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.*;
import java.nio.file.*;

public class TestGenSrgs extends TaskTest<GenSrgs>
{
    private DelayedFile delayed(TestResource testResource) throws IOException
    {
        return delayed(testResource.getFile(temporaryFolder));
    }

    private static DelayedFile delayed(File file) throws IOException
    {
        return new DelayedFile(TestGenSrgs.class, file);
    }

    private static void assertMatches(File file, String text) throws IOException
    {
        String fileText = new String(Files.readAllBytes(file.toPath()), Charsets.UTF_8);
        Assert.assertTrue("File " + file + " should contain: " + text, fileText.contains(text));
    }

    @Test
    public void runTask() throws IOException
    {
        File notchToSrg = temporaryFolder.newFile("notchToSrg.srg");
        File notchToMcp = temporaryFolder.newFile("notchToMcp.srg");
        File mcpToNotch = temporaryFolder.newFile("mcpToNotch.srg");
        File srgToMcp = temporaryFolder.newFile("srgToMcp.srg");
        File mcpToSrg = temporaryFolder.newFile("mcpToSrg.srg");
        File srgExc = temporaryFolder.newFile("srg.exc");
        File mcpExc = temporaryFolder.newFile("mcp.exc");
        // TODO: these could have contents, currently this test doesn't check exc/statics handling
        File inExc = temporaryFolder.newFile("in.exc");
        File statics = temporaryFolder.newFile("statics.exc");

        GenSrgs task = getTask(GenSrgs.class);
        task.setMethodsCsv(delayed(TestResource.METHODS_CSV));
        task.setFieldsCsv(delayed(TestResource.FIELDS_CSV));
        task.setInSrg(delayed(TestResource.OBFUSCATE_SRG));
        task.setNotchToSrg(delayed(notchToSrg));
        task.setNotchToMcp(delayed(notchToMcp));
        task.setMcpToNotch(delayed(mcpToNotch));
        task.setSrgToMcp(delayed(srgToMcp));
        task.setMcpToSrg(delayed(mcpToSrg));
        task.setSrgExc(delayed(srgExc));
        task.setMcpExc(delayed(mcpExc));
        task.setInExc(delayed(inExc));
        task.setInStatics(delayed(statics));
        task.doTask();

        assertMatches(notchToSrg, "MD: test/actual/NonDepUser/doStuffObf ()V test/actual/NonDepUser/method1 ()V");
        assertMatches(notchToMcp, "MD: test/actual/NonDepUser/doStuffObf ()V test/actual/NonDepUser/method1_unsrg ()V");
        assertMatches(mcpToNotch, "MD: test/actual/NonDepUser/method1_unsrg ()V test/actual/NonDepUser/doStuffObf ()V");
        assertMatches(mcpToSrg, "MD: test/actual/NonDepUser/method1_unsrg ()V test/actual/NonDepUser/method1 ()V");
        assertMatches(srgToMcp, "MD: test/actual/NonDepUser/method1 ()V test/actual/NonDepUser/method1_unsrg ()V");
    }
}

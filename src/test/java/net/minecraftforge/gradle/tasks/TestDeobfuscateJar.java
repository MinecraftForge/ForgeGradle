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

import net.minecraftforge.gradle.testsupport.JarComparison;
import net.minecraftforge.gradle.testsupport.TaskTest;
import net.minecraftforge.gradle.testsupport.TestResource;
import org.junit.Test;

import java.io.*;
import java.util.jar.*;

public class TestDeobfuscateJar extends TaskTest<DeobfuscateJar>
{

    @Test
    public void runTask() throws IOException
    {
        TestResource input = TestResource.ACTUAL_CLEAN_JAR;
        TestResource expected = TestResource.ACTUAL_OBF_JAR;
        TestResource srg = TestResource.OBFUSCATE_SRG;

        File outJar = temporaryFolder.newFile("out.jar");
        DeobfuscateJar deobfuscateJar = getTask(DeobfuscateJar.class);
        deobfuscateJar.setExceptorCfg(temporaryFolder.newFile("empty.exc"));
        deobfuscateJar.setSrg(srg.getFile(temporaryFolder));
        deobfuscateJar.setInJar(input.getFile(temporaryFolder));
        deobfuscateJar.setOutJar(outJar);
        deobfuscateJar.doTask();

        try (JarFile expectedJarFile = new JarFile(expected.getFile(temporaryFolder));
             JarFile outJarFile = new JarFile(outJar))
        {
            JarComparison.compareJarClassMembers(expectedJarFile, outJarFile);
        }
    }
}

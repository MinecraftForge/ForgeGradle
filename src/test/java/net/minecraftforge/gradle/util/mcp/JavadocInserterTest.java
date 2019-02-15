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
package net.minecraftforge.gradle.util.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.RemapSources;

public class JavadocInserterTest
{
    private static final String INPUT    = "JavadocInserterTest";
    private static final String EXPECTED = "JavadocInserterTestOut";

    @Test
    public void testJavadocInserter() throws IOException
    {
        String input = readResource(INPUT);

        ArrayList<String> newLines = new ArrayList<String>();
        for (String line : Constants.lines(input))
        {
            System.out.println(line);
            RemapSources.injectJavadoc(newLines, line, method -> "Javadoc For: " + method, field -> "Javadoc For: " + field);
            newLines.add(line);
        }
        String output = Joiner.on(Constants.NEWLINE).join(newLines);
        String[] expected = readResource(EXPECTED).split("\r\n|\r|\n");
        String[] actual = output.split("\r\n|\r|\n");

        //Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            System.out.println("EXPECTED >>"+expected[i]);
            System.out.println("ACTUAL   >>"+actual[i]);
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    private String readResource(String name) throws IOException
    {
        try (InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name))
        {
            return new String(ByteStreams.toByteArray(stream));
        }
    }
}

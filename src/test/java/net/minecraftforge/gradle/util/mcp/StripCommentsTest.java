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

import org.junit.Test;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Assert;

public class StripCommentsTest
{
    private static final String[] INPUTS    = new String[] {"StripComments", "UnendedString"};
    private static final String OUTPUT_POSTFIX = "Out";

    @Test
    public void testStripComments() throws IOException
    {
        for (String i : INPUTS)
        {
            String input = readResource(i);

            input = McpCleanup.stripComments(input);

            String[] expected = readResource(i + OUTPUT_POSTFIX).split("\r\n|\r|\n");
            String[] actual = input.split("\r\n|\r|\n");

            for (int k = 0; k < expected.length; k++)
            {
                System.out.println("EXPECTED >>"+expected[k]);
                System.out.println("ACTUAL   >>"+actual[k]);
                Assert.assertEquals(expected[k], actual[k]);
            }
        }
    }

    private String readResource(String name) throws IOException
    {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name);
        return new String(ByteStreams.toByteArray(stream));
    }
}

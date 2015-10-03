/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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

import net.minecraftforge.gradle.util.mcp.FmlCleanup;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;

public class FmlCleanupTest
{
    private static final String INPUT    = "AnonymousTest";
    private static final String EXPECTED = "AnonymousTestOut";
    
    @Test
    public void tstAnonymousClassRenaming() throws IOException
    {
        String input = readResource(INPUT);
        
        input = FmlCleanup.renameClass(input);
        
        String[] expected = readResource(EXPECTED).split("\r\n|\r|\n");
        String[] actual = input.split("\r\n|\r|\n");

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
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name);
        return new String(ByteStreams.toByteArray(stream));
    }
}

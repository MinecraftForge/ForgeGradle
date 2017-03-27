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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.RemapSources;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;

public class JavadocInserterTest
{
    private static final String INPUT    = "JavadocInserterTest";
    private static final String EXPECTED = "JavadocInserterTestOut";

    @Test
    public void tstJavadocInserter() throws IOException
    {
        String input = readResource(INPUT);

        ArrayList<String> newLines = new ArrayList<String>();
        for (String line : Constants.lines(input))
        {
            injectJavadoc(newLines, line);
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

    private void injectJavadoc(List<String> newLines, String line)
    {
        System.out.println(line);
        // methods
        Matcher matcher = RemapSources.METHOD.matcher(line);
        if (matcher.find())
        {
            String javadoc = "Javadoc For: " + matcher.group("name");
            if (!Strings.isNullOrEmpty(javadoc))
            {
                insetAboveAnnotations(newLines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));
            }

            // worked, so return and dont try the fields.
            return;
        }

        // fields
        matcher = RemapSources.FIELD.matcher(line);
        if (matcher.find())
        {
            String javadoc = "Javadoc For: " + matcher.group("name");
            if (!Strings.isNullOrEmpty(javadoc))
            {
                insetAboveAnnotations(newLines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, false));
            }
        }
    }

    private static void insetAboveAnnotations(List<String> list, String line)
    {
        int back = 0;
        while (list.get(list.size() - 1 - back).trim().startsWith("@"))
        {
            back++;
        }
        list.add(list.size() - back, line);
    }

    private String readResource(String name) throws IOException
    {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name);
        return new String(ByteStreams.toByteArray(stream));
    }
}

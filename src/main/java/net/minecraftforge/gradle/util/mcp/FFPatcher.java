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

import net.minecraftforge.gradle.common.Constants;

public class FFPatcher
{
    static final String MODIFIERS = "public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp";

    // Remove TRAILING whitespace
    private static final String TRAILING = "(?m)[ \\t]+$";

    //Remove repeated blank lines
    private static final String NEWLINES = "(?m)^(\\r\\n|\\r|\\n){2,}";

    public static String processFile(String text)
    {
        text = text.replaceAll(TRAILING, "");
        text = text.replaceAll(NEWLINES, Constants.NEWLINE);
        return text;
    }
}

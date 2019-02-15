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
package net.minecraftforge.gradle.util.delayed;

import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.Maps;

public class ReplacementProvider implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Map<String, String> replaceMap = Maps.newHashMap();

    public void putReplacement(String key, String value)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length() - 1) == '}')
        {
            key = key.substring(1, key.length() - 1);
        }

        replaceMap.put(key, value);
    }

    public boolean hasReplacement(String key)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length() - 1) == '}')
        {
            key = key.substring(1, key.length() - 1);
        }

        return replaceMap.containsKey(key);
    }
    
    public String get(String key)
    {
        return replaceMap.get(key);
    }
}

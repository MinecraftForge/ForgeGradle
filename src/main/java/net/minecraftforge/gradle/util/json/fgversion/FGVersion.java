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
package net.minecraftforge.gradle.util.json.fgversion;

import java.util.Arrays;

import com.google.gson.JsonObject;

public class FGVersion
{
    public String        version, docUrl;
    public String[]      changes, bugs;
    public FGBuildStatus status;
    public int           index;
    public JsonObject    ext;
    
    @Override
    public String toString()
    {
        return "FGVersion [version=" + version + ", docUrl=" + docUrl + ", changes=" + Arrays.toString(changes) + ", bugs=" + Arrays.toString(bugs) + ", status=" + status + ", index=" + index + ", ext=" + ext + "]";
    }
}

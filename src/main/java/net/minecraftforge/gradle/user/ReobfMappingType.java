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
package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.Constants;

/**
 * Preset mappings for Notch and Searge names.
 */
public enum ReobfMappingType
{
    SEARGE(Constants.SRG_MCP_TO_SRG),
    NOTCH(Constants.SRG_MCP_TO_NOTCH),
    CUSTOM(null);

    private String path;

    private ReobfMappingType(String s)
    {
        this.path = s;
    }

    /**
     * Gets the delayed path for this mapping type. Custom returns null.
     *
     * @return The delayed path
     */
    public String getPath()
    {
        return path;
    }
}

/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
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

package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;

import net.minecraftforge.gradle.common.util.Artifact;

import javax.inject.Inject;

public class MCPExtension {

    private Artifact config;
    public String pipeline;


    @Inject
    public MCPExtension(Project project) {
    }

    public Artifact getConfig() {
        return config;
    }

    public void setConfig(String value) {
        if (value.indexOf(':') != -1) // Full artifact
            config = Artifact.from(value);
        else
            config = Artifact.from("de.oceanlabs.mcp:mcp_config:" + value + "@zip");
    }

}

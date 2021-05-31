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

import net.minecraftforge.gradle.common.util.Artifact;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class MCPExtension {
    public static final String EXTENSION_NAME = "mcp";

    protected final Project project;

    @Inject
    public MCPExtension(final Project project) {
        this.project = project;
    }

    public abstract Property<Artifact> getConfig();

    public void setConfig(Provider<String> value) {
        getConfig().set(value.map(s -> {
            if (s.indexOf(':') != -1) { // Full artifact
                return Artifact.from(s);
            } else {
                return Artifact.from("de.oceanlabs.mcp:mcp_config:" + s + "@zip");
            }
        }));
    }

    public void setConfig(String value) {
        setConfig(project.provider(() -> value));
    }

    public abstract Property<String> getPipeline();
}

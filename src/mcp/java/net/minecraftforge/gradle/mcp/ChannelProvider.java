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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * A channel provider provides mapping files for its defined {@link #getChannels() channels} given a mapping channel and version.
 */
public interface ChannelProvider {
    /**
     * Returns an immutable set of the channels supported by this ChannelProvider.
     *
     * @return an immutable set of the channels supported by this ChannelProvider
     */
    @Nonnull
    Set<String> getChannels();

    /**
     * Attempts to generate a CSV zip mapping file based on the mapping channel and version.
     * This will only be called if {@code channel} is contained in {@link #getChannels()}.
     *
     * @param mcpRepo the MCP Repo instance used for querying data and generating cache locations
     * @param project the current project
     * @param channel the mappings channel, must be contained in {@link #getChannels()}
     * @param version the mappings version
     * @return a possibly-null mapping file location
     * @throws IOException if an I/O operation goes wrong
     */
    @Nullable
    File getMappingsFile(MCPRepo mcpRepo, Project project, String channel, String version) throws IOException;
}

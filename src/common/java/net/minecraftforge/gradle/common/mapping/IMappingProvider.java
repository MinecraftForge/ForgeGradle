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

package net.minecraftforge.gradle.common.mapping;

import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nullable;

import org.gradle.api.Project;
import net.minecraftforge.gradle.common.mapping.provider.McpMappingProvider;
import net.minecraftforge.gradle.common.mapping.provider.OfficialMappingProvider;

/**
 * @see McpMappingProvider
 * @see OfficialMappingProvider
 */
public interface IMappingProvider {

    /**
     * Channels should match the regex of [a-z_]+
     * @return The collection of channels that this provider handles.
     */
    Collection<String> getMappingChannels();

    /**
     * Supplies a location to an `mappings.zip`, generating/downloading it if necessary <br>
     * Channels should match the regex of [a-z_]+ <br>
     * Versions should be any maven artifact / filesystem compatible string <br>
     * @param project The current gradle project
     * @param channel The requested channel
     * @param version The requested version
     * @return An enhanced Supplier for the location of the `mappings.zip`
     * @throws IOException
     */
    @Nullable
    IMappingInfo getMappingInfo(Project project, String channel, String version) throws IOException;

}

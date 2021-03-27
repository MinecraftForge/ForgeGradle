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

package net.minecraftforge.gradle.common.mapping.provider;

import java.io.File;
import java.util.Collection;

import com.google.common.collect.Lists;
import org.gradle.api.Project;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.mapping.IMappingInfo;
import net.minecraftforge.gradle.common.mapping.IMappingProvider;
import net.minecraftforge.gradle.common.mapping.info.MappingInfo;

public class McpMappingProvider implements IMappingProvider {

    private void debug(Project project, String message) {
        if (BaseRepo.DEBUG) project.getLogger().lifecycle(message);
    }

    @Override
    public Collection<String> getMappingChannels() {
        return Lists.newArrayList("snapshot", "snapshot_nodoc", "stable", "stable_nodoc");
    }

    @Override
    public IMappingInfo getMappingInfo(Project project, String channel, String version) {
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";

        debug(project, "    Mapping: " + desc);
        File destination = MavenArtifactDownloader.manual(project, desc, false);

        return MappingInfo.of(channel, version, destination);
    }

    @Override
    public String toString() {
        return "MCP CrowdSourced Mappings";
    }
}
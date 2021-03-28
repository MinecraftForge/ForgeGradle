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
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Project;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.mapping.info.IMappingInfo;

public class McpMappingProvider implements IMappingProvider {

    private void debug(Project project, String message) {
        if (BaseRepo.DEBUG) project.getLogger().lifecycle(message);
    }

    private static final ImmutableSet<String> channels = ImmutableSet.of("snapshot", "snapshot_nodoc", "stable", "stable_nodoc");

    @Override
    public Set<String> getMappingChannels() {
        return channels;
    }

    @Override
    public IMappingInfo getMappingInfo(Project project, String channel, String version) {
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";

        debug(project, "    Mapping: " + desc);
        File destination = MavenArtifactDownloader.manual(project, desc, false);

        return IMappingInfo.of(channel, version, destination);
    }
}
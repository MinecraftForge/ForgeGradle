/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp;

import com.google.common.collect.ImmutableSet;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.util.Set;

class MCPChannelProvider implements ChannelProvider {
    @Nonnull
    @Override
    public Set<String> getChannels() {
        return ImmutableSet.of("snapshot", "snapshot_nodoc", "stable", "stable_nodoc");
    }

    @Nullable
    @Override
    public File getMappingsFile(MCPRepo mcpRepo, Project project, String channel, String version) {
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
        mcpRepo.debug("    Mapping: " + desc);
        return MavenArtifactDownloader.manual(project, desc, false);
    }
}

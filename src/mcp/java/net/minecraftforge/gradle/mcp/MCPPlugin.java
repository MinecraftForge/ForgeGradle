/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp;

import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.tasks.SetupMCP;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class MCPPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        // Needed to gain access to the JavaToolchainService as an extension
        project.getPluginManager().apply(JavaPlugin.class);

        MCPExtension extension = project.getExtensions().create("mcp", MCPExtension.class, project);

        TaskProvider<DownloadMCPConfig> downloadConfig = project.getTasks().register("downloadConfig", DownloadMCPConfig.class);
        TaskProvider<SetupMCP> setupMCP = project.getTasks().register("setupMCP", SetupMCP.class);

        downloadConfig.configure(task -> {
            task.getConfig().set(extension.getConfig().map(Artifact::getDescriptor));
            task.getOutput().set(project.getLayout().getBuildDirectory().file("mcp_config.zip"));
        });
        setupMCP.configure(task -> {
            task.getPipeline().set(extension.getPipeline());
            task.getConfig().set(downloadConfig.flatMap(DownloadMCPConfig::getOutput));
        });

        project.afterEvaluate(p -> {
            //Add Known repos
            project.getRepositories().maven(e -> {
                e.setUrl(Utils.MOJANG_MAVEN);
                e.metadataSources(MetadataSources::artifact);
            });
            project.getRepositories().maven(e -> {
                e.setUrl(Utils.FORGE_MAVEN);
                e.metadataSources(m -> {
                    m.gradleMetadata();
                    m.mavenPom();
                    m.artifact();
                });
            });
            project.getRepositories().mavenCentral(e -> e.mavenContent(c -> c.excludeGroup("net.minecraftforge"))); //Needed for MCP Deps; we do not publish any artufacts to maven central
        });
    }
}

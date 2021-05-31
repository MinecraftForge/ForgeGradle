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

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.tasks.SetupMCP;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class MCPPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        MCPExtension extension = project.getExtensions().create("mcp", MCPExtension.class, project);

        TaskProvider<DownloadMCPConfig> downloadConfig = project.getTasks().register("downloadConfig", DownloadMCPConfig.class);
        TaskProvider<SetupMCP> setupMCP = project.getTasks().register("setupMCP", SetupMCP.class);

        downloadConfig.configure(task -> {
            task.getConfig().set(extension.getConfig().toString());
            task.getOutput().set(project.file("build/mcp_config.zip"));
        });
        setupMCP.configure(task -> {
            task.dependsOn(downloadConfig);
            task.getPipeline().set(extension.getPipeline());
            task.getConfig().set(downloadConfig.get().getOutput());
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
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
        });
    }
}

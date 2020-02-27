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
import net.minecraftforge.gradle.mcp.function.DownloadClientFunction;
import net.minecraftforge.gradle.mcp.function.DownloadManifestFunction;
import net.minecraftforge.gradle.mcp.function.DownloadServerFunction;
import net.minecraftforge.gradle.mcp.function.DownloadVersionJSONFunction;
import net.minecraftforge.gradle.mcp.function.InjectFunction;
import net.minecraftforge.gradle.mcp.function.ListLibrariesFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.PatchFunction;
import net.minecraftforge.gradle.mcp.function.StripJarFunction;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class MCPPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        MCPExtension extension = project.getExtensions().create("mcp", MCPExtension.class, project);

        TaskProvider<DownloadMCPConfigTask> downloadConfig = project.getTasks().register("downloadConfig", DownloadMCPConfigTask.class);
        TaskProvider<SetupMCPTask> setupMCP = project.getTasks().register("setupMCP", SetupMCPTask.class);

        downloadConfig.configure(task -> {
            task.setConfig(extension.getConfig().toString());
            task.setOutput(project.file("build/mcp_config.zip"));
        });
        setupMCP.configure(task -> {
            task.dependsOn(downloadConfig);
            task.setPipeline(extension.pipeline);
            task.setConfig(downloadConfig.get().getOutput());
        });

        project.afterEvaluate(p -> {
            //Add Known repos
            project.getRepositories().maven(e -> {
                e.setUrl(Utils.MOJANG_MAVEN);
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().maven(e -> {
                e.setUrl(Utils.FORGE_MAVEN);
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
        });
    }

    public static MCPFunction createBuiltInFunction(String type) {
        switch (type) {
            case "downloadManifest":
                return new DownloadManifestFunction();
            case "downloadJson":
                return new DownloadVersionJSONFunction();
            case "downloadClient":
                return new DownloadClientFunction();
            case "downloadServer":
                return new DownloadServerFunction();
            case "strip":
                return new StripJarFunction();
            case "listLibraries":
                return new ListLibrariesFunction();
            case "inject":
                return new InjectFunction();
            case "patch":
                return new PatchFunction();
            default:
                return null;
        }
    }

}

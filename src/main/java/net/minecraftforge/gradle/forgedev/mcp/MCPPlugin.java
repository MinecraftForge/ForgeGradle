package net.minecraftforge.gradle.forgedev.mcp;

import net.minecraftforge.gradle.forgedev.mcp.function.DownloadClientFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.DownloadManifestFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.DownloadServerFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.DownloadVersionJSONFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.MCPFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.StripJarFunction;
import net.minecraftforge.gradle.forgedev.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.forgedev.mcp.task.DownloadMCPDependenciesTask;
import net.minecraftforge.gradle.forgedev.mcp.task.LoadMCPConfigTask;
import net.minecraftforge.gradle.forgedev.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.forgedev.mcp.task.ValidateMCPConfigTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class MCPPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        MCPExtension extension = project.getExtensions().create("mcp", MCPExtension.class, project);

        TaskProvider<DownloadMCPConfigTask> downloadConfig = project.getTasks().register("downloadConfig", DownloadMCPConfigTask.class);
        TaskProvider<LoadMCPConfigTask> loadConfig = project.getTasks().register("loadConfig", LoadMCPConfigTask.class);
        TaskProvider<ValidateMCPConfigTask> validateConfig = project.getTasks().register("validateConfig", ValidateMCPConfigTask.class);
        TaskProvider<DownloadMCPDependenciesTask> downloadDeps = project.getTasks().register("downloadDependencies", DownloadMCPDependenciesTask.class);
        TaskProvider<SetupMCPTask> setupMCP = project.getTasks().register("setupMCP", SetupMCPTask.class);

        downloadConfig.configure(task -> {
            task.config = extension.config;
        });
        loadConfig.configure(task -> {
            task.dependsOn(downloadConfig);
            task.pipeline = extension.pipeline;
            task.configFile = downloadConfig.get().output;
        });
        validateConfig.configure(task -> {
            task.dependsOn(loadConfig);
            task.unprocessed = loadConfig.get().rawConfig;
        });
        downloadDeps.configure(task -> {
            task.dependsOn(validateConfig);
            task.config = validateConfig.get().processed;
        });
        setupMCP.configure(task -> {
            task.dependsOn(validateConfig, downloadDeps);
            task.config = validateConfig.get().processed;
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
            default:
                return null;
        }
    }

}

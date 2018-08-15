package net.minecraftforge.gradle.mcp;

import net.minecraftforge.gradle.mcp.function.DownloadClientFunction;
import net.minecraftforge.gradle.mcp.function.DownloadManifestFunction;
import net.minecraftforge.gradle.mcp.function.DownloadServerFunction;
import net.minecraftforge.gradle.mcp.function.DownloadVersionJSONFunction;
import net.minecraftforge.gradle.mcp.function.InjectFunction;
import net.minecraftforge.gradle.mcp.function.ListLibrariesFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionOverlay;
import net.minecraftforge.gradle.mcp.function.PatchFunction;
import net.minecraftforge.gradle.mcp.function.StripJarFunction;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.DownloadMCPDependenciesTask;
import net.minecraftforge.gradle.mcp.task.LoadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.mcp.task.ValidateMCPConfigTask;
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
            task.setConfig(extension.config);
            task.setOutput(project.file("build/mcp_config.zip"));
        });
        loadConfig.configure(task -> {
            task.dependsOn(downloadConfig);
            task.setPipeline(extension.pipeline);
            task.setConfigFile(downloadConfig.get().getOutput());
        });
        validateConfig.configure(task -> {
            task.dependsOn(loadConfig);
            task.unprocessed = loadConfig.get().rawConfig;
        });
        downloadDeps.configure(task -> {
            task.dependsOn(validateConfig);
            task.setConfig(validateConfig.get().processed);
        });
        setupMCP.configure(task -> {
            task.dependsOn(validateConfig, downloadDeps);
            task.setConfig(validateConfig.get().processed);
            task.setAccessTransformers(extension.getAccessTransformers());
        });
    }

    public static MCPFunctionOverlay createFunctionOverlay(String type) {
        switch (type) {
            default:
                return null;
        }
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

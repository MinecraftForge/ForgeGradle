package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;

public class DownloadMCPDependenciesTask extends DefaultTask {

    private MCPConfig config;

    public MCPConfig getConfig() {
        return config;
    }

    @InputFile // Somewhat clean hack to support task caching
    public File getConfigFile() {
        return config.zipFile;
    }

    public void setConfig(MCPConfig config) {
        this.config = config;
    }

    @TaskAction
    public void downloadDependencies() {
        config.dependencies.forEach((name, future) -> {
            Set<File> artifact = MavenArtifactDownloader.download(getProject(), name);
            future.complete(artifact.iterator().next());
        });
    }

}

package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;

public class DownloadMCPDependenciesTask extends DefaultTask {

    @Input
    public MCPConfig config;

    @TaskAction
    public void downloadDependencies() {
        config.dependencies.forEach((name, future) -> {
            Set<File> artifact = MavenArtifactDownloader.download(getProject(), name);
            future.complete(artifact.iterator().next());
        });
    }

}

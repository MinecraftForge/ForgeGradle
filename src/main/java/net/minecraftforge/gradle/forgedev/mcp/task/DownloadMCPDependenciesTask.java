package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class DownloadMCPDependenciesTask extends DefaultTask {

    @Input
    public MCPConfig config;

    private int counter = 0;

    @TaskAction
    public void downloadDependencies() {
        config.dependencies.forEach((name, future) -> {
            Configuration cfg = getProject().getConfigurations().create("downloadDeps" + counter);
            cfg.getAllDependencies().add(getProject().getDependencies().create(name));
            getProject().getConfigurations().remove(cfg);

            future.complete(cfg.resolve().iterator().next());

            counter++;
        });
    }

}

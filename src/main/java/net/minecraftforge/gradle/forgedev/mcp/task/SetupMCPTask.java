package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class SetupMCPTask extends DefaultTask {

    @Input
    public MCPConfig config;

    @TaskAction
    public void setupMCP() {
        getLogger().info("Setting up MCP!");
        getLogger().info("Shared steps:");
        for(MCPConfig.Pipeline.Step step : config.pipeline.sharedSteps) {
            getLogger().info("Executing step '%s'", step.type);
        }
        getLogger().info("Source steps:");
        for(MCPConfig.Pipeline.Step step : config.pipeline.srcSteps) {
            getLogger().info("Executing step '%s'", step.type);
        }
    }

}

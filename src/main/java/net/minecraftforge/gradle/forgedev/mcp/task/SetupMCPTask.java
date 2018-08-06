package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import net.minecraftforge.gradle.forgedev.mcp.util.MCPRuntime;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class SetupMCPTask extends DefaultTask {

    public String skip;

    @Input
    public MCPConfig config;

    @TaskAction
    public void setupMCP() throws Exception {
        getLogger().info("Setting up MCP!");
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true);
        runtime.execute(getLogger(), skip.split(","));
    }

    @Option(option = "skip", description = "Comma-separated list of tasks to be skipped")
    public void setSkipped(String skip) {
        this.skip = skip;
    }
}

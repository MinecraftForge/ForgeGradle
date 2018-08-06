package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import net.minecraftforge.gradle.forgedev.mcp.util.MCPRuntime;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public class SetupMCPTask extends DefaultTask {

    @Input
    public MCPConfig config;

    @TaskAction
    public void setupMCP() throws Exception {
        getLogger().info("Setting up MCP!");
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true);
        runtime.execute(getLogger());
    }

}

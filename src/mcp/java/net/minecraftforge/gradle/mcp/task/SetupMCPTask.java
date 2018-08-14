package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;

public class SetupMCPTask extends DefaultTask {

    private List<String> accessTransformers;
    private MCPConfig config;

    private File output;

    public void setAccessTransformers(List<String> accessTransformers) {
        this.accessTransformers = accessTransformers;
    }

    @Input
    public List<String> getAccessTransformers() {
        return accessTransformers;
    }

    public MCPConfig getConfig() {
        return config;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setConfig(MCPConfig config) {
        this.config = config;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @TaskAction
    public void setupMCP() throws Exception {
        getLogger().info("Setting up MCP!");
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true);
        File out = runtime.execute(getLogger());
        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }

}

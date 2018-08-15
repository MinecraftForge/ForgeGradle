package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.List;

public class SetupMCPTask extends DefaultTask {

    private List<String> accessTransformers;
    private MCPConfig config;

    private File output = getProject().file("build/" + getName() + "/output.zip");

    @Input
    public List<String> getAccessTransformers() {
        return accessTransformers;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public MCPConfig getConfig() {
        return config;
    }

    @InputFile // Somewhat clean hack to support task caching
    public File getConfigFile() {
        return config.zipFile;
    }

    public void setAccessTransformers(List<String> accessTransformers) {
        this.accessTransformers = accessTransformers;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setConfig(MCPConfig config) {
        this.config = config;
    }

    @TaskAction
    public void setupMCP() throws Exception {
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true);
        File out = runtime.execute(getLogger());
        if (FileUtils.contentEquals(out, output)) return;
        Utils.delete(output);
        FileUtils.copyFile(out, output);
    }

}

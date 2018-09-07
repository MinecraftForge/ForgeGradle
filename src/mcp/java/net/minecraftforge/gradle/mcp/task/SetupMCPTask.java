package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SetupMCPTask extends DefaultTask {

    private Map<String, MCPFunction> extrasPre = new LinkedHashMap<>();
    private MCPConfig config;

    private File output = getProject().file("build/" + getName() + "/output.zip");

    public SetupMCPTask() {
        this.getOutputs().upToDateWhen(task -> {
            HashStore cache = new HashStore(getProject());
            try {
                cache.load(getProject().file("build/" + getName() + "/inputcache.sha1"));
                cache.add("configFile", getConfigFile());
                extrasPre.forEach((key, func) -> func.addInputs(cache, key + "."));
                cache.save();
                return cache.isSame() && getOutput().exists();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
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

    public void setOutput(File output) {
        this.output = output;
    }

    public void setConfig(MCPConfig config) {
        this.config = config;
    }

    @TaskAction
    public void setupMCP() throws Exception {
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true, extrasPre);
        File out = runtime.execute(getLogger());
        if (FileUtils.contentEquals(out, output)) return;
        Utils.delete(output);
        FileUtils.copyFile(out, output);
    }

    public void addPreDecompile(String name, MCPFunction function) {
        this.extrasPre.put(name, function);
    }

    //TODO: Not hardcode names
    public File getClientJar() {
        return getProject().file("build/mcp/downloadClient/client.jar");
    }
    public File getServerJar() {
        return getProject().file("build/mcp/downloadServer/server.jar");
    }
    public File getJoinedJar() {
        return getProject().file("build/mcp/merge/output.jar");
    }

}

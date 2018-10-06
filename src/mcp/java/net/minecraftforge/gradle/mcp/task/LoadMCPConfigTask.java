package net.minecraftforge.gradle.mcp.task;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadMCPConfigTask extends DefaultTask {

    private static final String CONFIG_FILE_NAME = "config.json";

    private final Gson gson = new Gson();

    private File configFile;
    private String pipeline;
    private final CompletableFuture<MCPConfig> config = new CompletableFuture<>();

    @InputFile
    public File getConfigFile() {
        return configFile;
    }

    @Input
    public String getPipeline() {
        return this.pipeline;
    }

    public CompletableFuture<MCPConfig> getConfig() {
        return this.config;
    }

    public void setConfigFile(File value) {
        this.configFile = value;
    }

    public void setPipeline(String value) {
        this.pipeline = value;
    }

    @TaskAction
    public void load() throws IOException {
        JsonObject json;
        try (ZipFile zip = new ZipFile(configFile)) {

            ZipEntry configEntry = zip.getEntry(CONFIG_FILE_NAME);
            if (configEntry == null) {
                throw new IllegalStateException("Could not find '" + CONFIG_FILE_NAME + "' in " + configFile.getAbsolutePath());
            }

            try (InputStream configStream = zip.getInputStream(configEntry)) {
                json = gson.fromJson(new InputStreamReader(configStream), JsonObject.class);
            }
        }

        if (json != null) {
            MCPConfig cfg = MCPConfig.deserialize(getProject(), configFile, json, pipeline);
            this.config.complete(cfg);
        }
    }

}

package net.minecraftforge.gradle.common.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.MCPConfigV1;

public class ExtractMCPData extends DefaultTask {
    private static final Gson GSON = new GsonBuilder().create();

    private String key = "mappings";
    private File config;
    private File output = getProject().file("build/" + getName() + "/output.srg");


    @TaskAction
    public void run() throws IOException {
        try (ZipFile zip = new ZipFile(getConfig())) {
            ZipEntry entry = zip.getEntry("config.json");
            if (entry == null) {
                throw new IllegalStateException("Could not find 'config.json' in " + getConfig().getAbsolutePath());
            }
            int spec = Config.getSpec(zip.getInputStream(entry));
            if (spec == 1) {
                MCPConfigV1 cfg = GSON.fromJson(new InputStreamReader(zip.getInputStream(entry)), MCPConfigV1.class);
                String path = cfg.getData(key.split("/"));
                if (path == null && "statics".equals(key)) { //TODO: Remove when I next push MCPConfig
                    path = "config/static_methods.txt";
                }
                if (path == null) {
                    throw new IllegalStateException("Could not find data entry for '" + key + "'");
                }
                entry = zip.getEntry(path);
                if (entry == null) {
                    throw new IllegalStateException("Invalid config zip, Missing path '" + path + "'");
                }
                try (OutputStream out = new FileOutputStream(getOutput())) {
                    IOUtils.copy(zip.getInputStream(entry), out);
                }
            } else {
                throw new IllegalStateException("Unsupported spec version '" + spec + "'");
            }
        }
    }

    @InputFile
    public File getConfig() {
        return config;
    }
    public void setConfig(File value) {
        this.config = value;
    }

    @Input
    public String getKey() {
        return this.key;
    }
    public void setKey(String value) {
        this.key = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

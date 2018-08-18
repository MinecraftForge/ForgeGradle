package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.Library;

public class TaskInjectDependencies extends DefaultTask {
    private File meta;
    private String config;

    public TaskInjectDependencies() {
        this.getOutputs().upToDateWhen(a -> false);
    }

    @TaskAction
    public void run() throws IOException {
        VersionJson json = Utils.loadJson(getMeta(), VersionJson.class);
        for (Library lib : json.libraries) {
            getProject().getDependencies().add(getConfig(), lib.name);
        }
        getProject().getDependencies().add(getConfig(), "com.google.code.findbugs:jsr305:3.0.1"); //TODO: This is included in MCPConfig, Need to feed that as input to this...
        getProject().getConfigurations().getByName(getConfig()).resolve(); //TODO: How to let gradle auto-resolve these?
    }

    @InputFile
    public File getMeta() {
        return this.meta;
    }
    public void setMeta(File value) {
        this.meta = value;
    }
    @Input
    public String getConfig() {
        return this.config;
    }
    public void setConfig(String value) {
        this.config = value;
    }
}

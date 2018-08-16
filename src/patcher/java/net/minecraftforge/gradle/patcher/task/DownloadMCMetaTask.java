package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class DownloadMCMetaTask extends DefaultTask {

    private String mcVersion;
    private File manifest = getProject().file("build/" + getName() + "/manifest.json");
    private File output = getProject().file("build/" + getName() + "/version.json");

    @TaskAction
    public void downloadMCMeta() {

    }

    @Input
    public String getMCVersion() {
        return mcVersion;
    }

    public File getManifest() {
        return manifest;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public void setManifest(File manifest) {
        this.manifest = manifest;
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

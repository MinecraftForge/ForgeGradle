package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.LibraryDownload;

public class TaskExtractNatives extends DefaultTask {
    private File meta;
    private File output;

    @TaskAction
    public void run() throws IOException {
        VersionJson json = Utils.loadJson(getMeta(), VersionJson.class);
        for (LibraryDownload lib : json.getNatives()) {
            File target = Utils.getCache(getProject(), "libraries", lib.path);
            Utils.updateDownload(getProject(), target, lib);
            Utils.extractZip(target, getOutput(), false);
        }
    }

    @InputFile
    public File getMeta() {
        return this.meta;
    }
    public void setMeta(File value) {
        this.meta = value;
    }

    @OutputDirectory
    public File getOutput() {
        return this.output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

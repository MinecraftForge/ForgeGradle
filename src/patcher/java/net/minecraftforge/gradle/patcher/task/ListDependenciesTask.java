package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class ListDependenciesTask extends DefaultTask {

    private File versionMeta;
    private final Set<File> output = new HashSet<>();

    @TaskAction
    public void listDependencies() {

    }

    @InputFile
    public File getVersionMeta() {
        return versionMeta;
    }

    public Set<File> getOutput() {
        return output;
    }

    public void setVersionMeta(File file) {
        this.versionMeta = file;
    }

}

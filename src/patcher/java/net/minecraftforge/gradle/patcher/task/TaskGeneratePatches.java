package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class TaskGeneratePatches extends DefaultTask {

    private File clean;
    private File modified;
    private File patches;

    @TaskAction
    public void generatePatches() {

    }

    @InputFile
    public File getClean() {
        return clean;
    }

    @InputDirectory
    public File getModified() {
        return modified;
    }

    @OutputDirectory
    public File getPatches() {
        return patches;
    }

    public void setClean(File clean) {
        this.clean = clean;
    }

    public void setModified(File modified) {
        this.modified = modified;
    }

    public void setPatches(File patches) {
        this.patches = patches;
    }

}

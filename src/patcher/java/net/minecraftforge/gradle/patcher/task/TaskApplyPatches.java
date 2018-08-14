package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class TaskApplyPatches extends DefaultTask {

    private File clean;
    private File patches;
    private File output = getProject().file("build/" + getName() + "/output.zip");

    @TaskAction
    public void applyPatches() {
        getProject().getLogger().lifecycle(clean.getAbsolutePath());
    }

    @InputFile
    public File getClean() {
        return clean;
    }

    @InputDirectory
    public File getPatches() {
        return patches;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setClean(File clean) {
        this.clean = clean;
    }

    public void setPatches(File value) {
        patches = value;
    }

    public void setOutput(File value) {
        output = value;
    }

}

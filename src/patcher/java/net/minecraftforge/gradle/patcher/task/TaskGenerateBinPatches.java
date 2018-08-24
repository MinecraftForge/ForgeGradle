package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class TaskGenerateBinPatches extends DefaultTask {

    private File rawJar;
    private File patchedJar;
    // FIXME: Stop using pack200. It's deprecated in J11 and will be gone soon. https://bugs.openjdk.java.net/browse/JDK-8199871
    private File output = getProject().file("build/" + getName() + "/output.pack.lzma");

    @TaskAction
    public void genBinPatches() {

    }

    @InputFile
    public File getRawJar() {
        return rawJar;
    }

    @InputFile
    public File getPatchedJar() {
        return patchedJar;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setRawJar(File rawJar) {
        this.rawJar = rawJar;
    }

    public void setPatchedJar(File patchedJar) {
        this.patchedJar = patchedJar;
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

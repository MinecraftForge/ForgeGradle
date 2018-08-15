package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class TaskMCPToSRG extends DefaultTask {

    private File input;
    private File mappings;
    private File output = getProject().file("build/" + getName() + "/output.zip");

    @TaskAction
    public void applyMappings() {

    }

    @InputDirectory
    public File getInput() {
        return input;
    }

    @InputFile
    public File getMappings() {
        return mappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setInput(File input) {
        this.input = input;
    }

    public void setMappings(File mappings) {
        this.mappings = mappings;
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

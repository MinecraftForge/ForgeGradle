package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;

public class TaskReobfuscateJar extends DefaultTask {

    private File input;
    private File srgMappings;
    private File mcpMappings;
    private File output = getProject().file("build/" + getName() + "/output.jar");

    @TaskAction
    public void reobfuscate() {

    }

    @InputFile
    public File getInput() {
        return input;
    }

    @InputFile
    public File getSrgMappings() {
        return srgMappings;
    }

    @InputFile
    public File getMcpMappings() {
        return mcpMappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setInput(File input) {
        this.input = input;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setSrgMappings(File srgMappings) {
        this.srgMappings = srgMappings;
    }

    public void setMcpMappings(File mcpMappings) {
        this.mcpMappings = mcpMappings;
    }

}

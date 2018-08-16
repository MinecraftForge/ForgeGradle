package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class TaskApplyRangeMap extends DefaultTask {

    private File sources;
    private File rangeMap;
    private Set<File> mappings = new HashSet<>();
    private Set<File> excs = new HashSet<>();
    private File output = getProject().file("build/" + getName() + "/output.zip");

    @TaskAction
    public void applyRangeMap() {

    }

    @InputDirectory
    public File getSources() {
        return sources;
    }

    @InputFile
    public File getRangeMap() {
        return rangeMap;
    }

    @InputFiles
    public Set<File> getMappings() {
        return mappings;
    }

    @InputFiles
    public Set<File> getExcs() {
        return excs;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setSources(File sources) {
        this.sources = sources;
    }

    public void setRangeMap(File rangeMap) {
        this.rangeMap = rangeMap;
    }

    public void setMappings(Set<File> mappings) {
        this.mappings = mappings;
    }

    public void setExcs(Set<File> excs) {
        this.excs = excs;
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

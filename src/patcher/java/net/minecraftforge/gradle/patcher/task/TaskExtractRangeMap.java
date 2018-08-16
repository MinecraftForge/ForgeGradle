package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;

public class TaskExtractRangeMap extends DefaultTask {

    private Set<File> sources;
    private Set<File> dependencies;
    private File output = getProject().file("build/" + getName() + "/output.???");// TODO: Specify an output format

    @TaskAction
    public void extractRangeMap() {

    }

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }

    @InputFiles
    public Set<File> getDependencies() {
        return dependencies;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setSources(Set<File> sources) {
        this.sources = sources;
    }

    public void setDependencies(Set<File> dependencies) {
        this.dependencies = dependencies;
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

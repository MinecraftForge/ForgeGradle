package net.minecraftforge.gradle.userdev.tasks;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ApplyBinPatches extends JarExec {
    private Supplier<File> clean;
    private File input;
    private File output;

    public ApplyBinPatches() {
        tool = "net.minecraftforge:binarypatcher:1.+:fatjar";
        args = new String[] { "--clean", "{clean}", "--output", "{output}", "--apply", "{patch}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{clean}", getClean().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{patch}", getPatch().getAbsolutePath());

        return Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

    @InputFile
    public File getClean() {
        return clean.get();
    }
    public void setClean(File value) {
        this.clean = () -> value;
    }
    public void setClean(Supplier<File> value) {
        this.clean = value;
    }

    @InputFile
    public File getPatch() {
        return input;
    }
    public void setPatch(File value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            setOutput(getProject().file("build/" + getName() + "/output.jar"));
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

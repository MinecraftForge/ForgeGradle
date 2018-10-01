package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GenerateBinPatches extends JarExec {
    private File cleanJar;
    private File dirtyJar;
    private File srg;
    private Set<File> patchSets = new HashSet<>();
    private String side;
    private File output = null;

    public GenerateBinPatches() {
        tool = "net.minecraftforge:binarypatcher:1.+:fatjar";
        args = new String[] { "--clean", "{clean}", "--create", "{dirty}", "--output", "{output}", "--patches", "{patches}", "--srg", "{srg}"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{clean}", getCleanJar().getAbsolutePath());
        replace.put("{dirty}", getDirtyJar().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{srg}", getSrg().getAbsolutePath());

        List<String> _args = new ArrayList<>();
        for (String arg : getArgs()) {
            if ("{patches}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getPatchSets().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }
        return _args;
    }

    @InputFile
    public File getCleanJar() {
        return cleanJar;
    }
    public void setCleanJar(File value) {
        this.cleanJar = value;
    }

    @InputFile
    public File getDirtyJar() {
        return dirtyJar;
    }
    public void setDirtyJar(File value) {
        this.dirtyJar = value;
    }

    @InputFiles
    public Set<File> getPatchSets() {
        return this.patchSets;
    }
    public void addPatchSet(File value) {
        if (value != null) {
            this.patchSets.add(value);
        }
    }

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Input
    public String getSide() {
        return this.side;
    }
    public void setSide(String value) {
        this.side = value;
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/" + getSide() + ".lzma"));
        }
    }

    @OutputFile
    public File getOutput() {
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/output.lzma"));
        }
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

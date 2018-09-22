package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.ChainedInputSupplier;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class TaskApplyRangeMap extends DefaultTask {

    //private Set<String> srgExtra = new HashSet<>(); //TODO: Make S2S read strings easier
    //private Set<String> excExtra = new HashSet<>(); //TODO: Make S2S read strings easier
    private Set<File> srgs = new HashSet<>();
    private Set<File> excs = new HashSet<>();
    private Set<File> sources = new HashSet<>();

    private File rangeMap;
    public boolean annotate = false;
    public boolean keepImports = true;

    private File output = getProject().file("build/" + getName() + "/output.zip");
    private File log = getProject().file("build/" + getName() + "/log.txt");

    @TaskAction
    public void applyRangeMap() throws IOException {
        RangeApplier apply = new RangeApplier();

        apply.readSrg(getSrgFiles());
        apply.readParamMap(getExcFiles());
        apply.setKeepImports(getKeepImports());

        try (FileOutputStream fos = new FileOutputStream(log);
            OutputSupplier out = new ZipOutputSupplier(getOutput())) {
            apply.setOutLogger(new PrintStream(fos));
            apply.remapSources(getInputSupplier(), out, getRangeMap(), getAnnotate());
        }
    }

    @SuppressWarnings("resource")
    private InputSupplier getInputSupplier() throws IOException {
        ChainedInputSupplier inputs = new ChainedInputSupplier();
        for (File src : getSources()) {
            if (src.exists()) {
                inputs.add(src.isDirectory() ? new FolderSupplier(src) : new ZipInputSupplier(src));
            }
        }
        return inputs.shrink();
    }

    @InputFiles
    public Set<File> getSrgFiles() {
        return this.srgs;
    }
    public void setSrgFiles(File... values) {
        for (File value : values) {
            this.srgs.add(value);
        }
    }
    /*
    @Input
    public Set<String> getSrgExtra() {
        return this.srgExtra;
    }
    public void setSrg(String... values) {
        for (String val : values) {
            this.srgExtra.add(val);
        }
    }
    */

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }
    public void setSources(Collection<File> values) {
        this.sources.addAll(values);
    }
    public void setSources(File... values) {
        for (File value : values) {
            this.sources.add(value);
        }
    }

    @InputFile
    public File getRangeMap() {
        return rangeMap;
    }
    public void setRangeMap(File value) {
        this.rangeMap = value;
    }
    @Input
    public boolean getAnnotate() {
        return annotate;
    }
    public void setAnnotate(boolean value) {
        this.annotate = value;
    }
    @Input
    public boolean getKeepImports() {
        return keepImports;
    }
    public void setKeepImports(boolean value) {
        this.keepImports = value;
    }

    @InputFiles
    public Set<File> getExcFiles() {
        return excs;
    }
    public void setExcFiles(File... values) {
        for (File value : values) {
            this.excs.add(value);
        }
    }
    public void setExcFiles(Collection<File> values) {
        this.excs.addAll(values);
    }
    /*
    @Input
    public Set<String> getExcExtra() {
        return this.excExtra;
    }
    public void setExc(String... values) {
        for (String val : values) {
            this.excExtra.add(val);
        }
    }
    */

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

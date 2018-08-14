package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.util.function.Supplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Suppliers;

public class TaskApplyPatches extends DefaultTask {
    private Supplier<File> _base;
    @InputFile public File getBase() { return _base.get(); }
    public Supplier<File> getBaseLazy() { return _base; }
    public void setBase(Supplier<File> value) { _base = Suppliers.memoize(value::get); }
    public void setBase(File value) { _base = () -> value; }


    private Supplier<File> _patches;
    @InputFile public File getPatches() { return _patches.get(); }
    public Supplier<File> getPatchesLazy() { return _patches; }
    public void setPatches(Supplier<File> value) { _patches = Suppliers.memoize(value::get); }
    public void setPatches(File value) { _patches = () -> value; }

    private Supplier<File> _output;
    @OutputFile public File getOutput() { return _output.get(); }
    public Supplier<File> getOutputLazy() { return _output; }
    public void setOutput(Supplier<File> value) { _output = Suppliers.memoize(value::get); }
    public void setOutput(File value) { _output = () -> value; }

    @TaskAction
    public void applyPatches() {
        getProject().getLogger().lifecycle(getBase().getAbsolutePath());
    }

}

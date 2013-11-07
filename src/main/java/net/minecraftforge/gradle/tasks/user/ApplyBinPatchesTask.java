package net.minecraftforge.gradle.tasks.user;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class ApplyBinPatchesTask extends CachedTask
{
    @InputFile
    DelayedFile inJar;

    @OutputFile
    DelayedFile outJar;

    @InputFile
    DelayedFile patches;  // this will be a patches.lzma    lzma'd and zipped.
    
    @TaskAction
    public void doTask()
    {
        // do stuff here.
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }

    public File getPatchesJar()
    {
        return patches.call();
    }

    public void setPatchesJar(DelayedFile patchesJar)
    {
        this.patches = patchesJar;
    }
    
    
}

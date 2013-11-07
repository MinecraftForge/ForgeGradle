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
    
    @InputFile
    DelayedFile srg;

    @OutputFile
    @Cached
    DelayedFile outJar;

    @InputFile
    DelayedFile patches;  // this will be a patches.lzma
    
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

    public File getPatches()
    {
        return patches.call();
    }

    public void setPatches(DelayedFile patchesJar)
    {
        this.patches = patchesJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }
    
    
}

package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.util.LinkedList;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class AmalgamateMappingsTask extends DefaultTask
{
    @InputFile
    private DelayedFile                   originalSrg;
    @InputFile
    private DelayedFile                   originalExc;
    
    @OutputFile
    private DelayedFile                   outSrg;
    @OutputFile
    private DelayedFile                   outExc;

    @InputFiles
    private final LinkedList<DelayedFile> extraExcs = new LinkedList<DelayedFile>();
    @InputFiles
    private final LinkedList<DelayedFile> extraSrgs = new LinkedList<DelayedFile>();
    
    @TaskAction
    public void doTask()
    {
        
    }

    public File getOriginalSrg()
    {
        return originalSrg.call();
    }

    public void setOriginalSrg(DelayedFile originalSrg)
    {
        this.originalSrg = originalSrg;
    }

    public File getOriginalExc()
    {
        return originalExc.call();
    }

    public void setOriginalExc(DelayedFile originalExc)
    {
        this.originalExc = originalExc;
    }
    
    public File getOutSrg()
    {
        return outSrg.call();
    }

    public void setOutSrg(DelayedFile originalSrg)
    {
        this.outSrg = originalSrg;
    }

    public File getOutlExc()
    {
        return outExc.call();
    }

    public void setOutExc(DelayedFile originalExc)
    {
        this.outExc = originalExc;
    }

    public FileCollection getExtraExcs()
    {
        return getProject().files(extraExcs);
    }
    
    public void addExtraExc(DelayedFile file)
    {
        extraExcs.add(file);
    }

    public FileCollection getExtraSrgs()
    {
        return getProject().files(extraSrgs);
    }
    
    public void addExtraSrg(DelayedFile file)
    {
        extraSrgs.add(file);
    }
}

package net.minecraftforge.gradle.tasks;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

public class ApplySrg2Source extends DefaultTask
{
    @InputFile
    private DelayedFile rangeMap;
    
    private DelayedFile in;
    private DelayedFile out;
    
    @TaskAction
    public void doTask()
    {
        // TODO
    }

    public File getRangeMap()
    {
        return rangeMap.call();
    }

    public void setRangeMap(DelayedFile rangeMap)
    {
        this.rangeMap = rangeMap;
    }

    @InputFiles
    public FileCollection getIns()
    {
        File inFile = in.call();
        if (inFile.isDirectory())
            return getProject().fileTree(inFile);
        else
            return getProject().files(inFile);
    }
    
    public File getIn()
    {
        return in.call();
    }

    public void setIn(DelayedFile in)
    {
        this.in = in;
    }
    
    @OutputFiles
    public FileCollection getOuts()
    {
        File inFile = in.call();
        if (inFile.isDirectory())
            return getProject().fileTree(inFile);
        else
            return getProject().files(inFile);
    }

    public File getOut()
    {
        return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }
    
    
}

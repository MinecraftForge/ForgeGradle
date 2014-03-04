package net.minecraftforge.gradle.tasks;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class BuildRangeMapTask extends DefaultTask
{
    @Input
    String libConfig; // later converted to a list of dependencies
    
    DelayedFile src; // later converted into a FileCollection containing atleast 1 file.
    
    @OutputFile
    private DelayedFile rangeMap;
    
    @TaskAction
    public void doTask()
    {
        // TODO
    }

    public String getLibConfig()
    {
        return libConfig;
    }

    public void setLibConfig(String libConfig)
    {
        this.libConfig = libConfig;
    }
    
    @InputFiles
    public FileCollection getLibs()
    {
        return getProject().getConfigurations().getByName(libConfig);
    }

    @InputFiles
    public FileCollection getSources()
    {
        File inFile = src.call();
        if (inFile.isDirectory())
            return getProject().fileTree(inFile);
        else
            return getProject().files(inFile);
    }
    
    public File getSrc()
    {
        return src.call();
    }

    public void setSrc(DelayedFile src)
    {
        this.src = src;
    }

    public File getRangeMap()
    {
        return rangeMap.call();
    }

    public void setRangeMap(DelayedFile rangeMap)
    {
        this.rangeMap = rangeMap;
    }
}

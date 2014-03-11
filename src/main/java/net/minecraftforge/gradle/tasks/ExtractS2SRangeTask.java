package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class ExtractS2SRangeTask extends DefaultTask
{
    @InputFiles
    private FileCollection libs;
    private DelayedFile projectFile; // to get classpath from a subproject
    private String projectConfig; // Also for a subProject
    
    // stuff defined on the tasks..
    private DelayedFile in;
    
    @OutputFile
    private DelayedFile rangeMap;
    
    @TaskAction
    public void doTask() throws IOException
    {
        File in = getIn();
        File rangemap = getRangeMap();
        
        InputSupplier inSup;
        
        boolean isJar = in.getPath().endsWith(".jar") || in.getPath().endsWith(".zip");
        
        if (isJar)
        {
            // setup input
            inSup = new ZipInputSupplier();
            ((ZipInputSupplier) inSup).readZip(in);
        }
        else // folder!
        {
            inSup = new FolderSupplier(in);
        }
        
        generateRangeMap(inSup, rangemap);
    }
    
    private void generateRangeMap(InputSupplier inSup, File rangeMap)
    {
        RangeExtractor extractor = new RangeExtractor();
        extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);
        
        PrintStream stream = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
        extractor.setOutLogger(stream);
        
        boolean worked = extractor.generateRangeMap(rangeMap);
        
        stream.close();
        
        if (!worked)
            throw new RuntimeException("RangeMap generation Failed!!!");
    }
    
    public File getRangeMap()
    {
        return rangeMap.call();
    }

    public void setRangeMap(DelayedFile out)
    {
        this.rangeMap = out;
    }

    @InputFiles
    public FileCollection getIns()
    {
        File inFile = getIn();
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
    
    public FileCollection getLibs()
    {
        if (projectFile != null && libs == null) // libs == null to avoid doing this any more than necessary..
        {
            Project proj = BasePlugin.getProject(projectFile.call(), getProject());
            libs = proj.getConfigurations().getByName(projectConfig);
        }
        
        return libs;
    }

    public void setLibs(FileCollection libs)
    {
        this.libs = libs;
    }
    
    public void setLibsFromProject(DelayedFile buildscript, String config)
    {
        this.projectFile = buildscript;
        this.projectConfig = config;
    }
}

package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.gradle.SequencedInputSupplier;
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
import org.gradle.api.internal.AbstractTask;
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
    private boolean includeJar = false; //Include the 'jar' task for subproject.
    
    // stuff defined on the tasks..
    private final List<DelayedFile> in = new LinkedList<DelayedFile>();
    
    @OutputFile
    private DelayedFile rangeMap;
    
    @TaskAction
    public void doTask() throws IOException
    {
        List<File> ins = getIn();
        File rangemap = getRangeMap();
        
        InputSupplier inSup;
        
        if (ins.size() == 0)
            return; // no input.
        else if (ins.size() == 1)
        {
            // just 1 supplier.
            inSup = getInput(ins.get(0));
        }
        else
        {
            // multinput
            inSup = new SequencedInputSupplier();
            for (File f : ins)
                ((SequencedInputSupplier) inSup).add(getInput(f));
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
    
    private InputSupplier getInput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            ZipInputSupplier supp = new ZipInputSupplier();
            supp.readZip(f);
            return supp;
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
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
        return getProject().files(in);
    }
    
    public List<File> getIn()
    {
        List<File> files = new LinkedList<File>();
        for (DelayedFile f : in)
            files.add(f.call());
        return files;
    }

    public void addIn(DelayedFile in)
    {
        this.in.add(in);
    }
    
    public FileCollection getLibs()
    {
        if (projectFile != null && libs == null) // libs == null to avoid doing this any more than necessary..
        {
            Project proj = BasePlugin.getProject(projectFile.call(), getProject());
            libs = proj.getConfigurations().getByName(projectConfig);

            if (includeJar)
            {
                AbstractTask jarTask = (AbstractTask)proj.getTasks().getByName("jar");
                File compiled = (File)jarTask.property("archivePath");
                libs = getProject().files(compiled, libs);
            }
        }
        
        return libs;
    }

    public void setLibs(FileCollection libs)
    {
        this.libs = libs;
    }
    
    public void setLibsFromProject(DelayedFile buildscript, String config, boolean includeJar)
    {
        this.projectFile = buildscript;
        this.projectConfig = config;
        this.includeJar = includeJar;
    }
}

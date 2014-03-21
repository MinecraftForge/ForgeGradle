package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.gradle.SequencedInputSupplier;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

public class ApplyS2STask extends DefaultTask
{
    @InputFiles
    private final List<Object> srg = new LinkedList<Object>();

    @Optional
    @InputFiles
    private final List<Object> exc = new LinkedList<Object>();
    
    @InputFile
    private DelayedFile rangeMap;
    
    // stuff defined on the tasks..
    private final List<DelayedFile> in = new LinkedList<DelayedFile>();
    private DelayedFile out;
    
    @TaskAction
    public void doTask() throws IOException
    {
        List<File> ins = getIn();
        File out = getOut();
        File rangemap = getRangeMap();
        File rangelog = File.createTempFile("rangelog", ".txt", this.getTemporaryDir());
        FileCollection srg = getSrgs();
        FileCollection exc = getExcs();
        
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
        
        OutputSupplier outSup;
        if (ins.size() == 1 && ins.get(0).equals(out) && ins instanceof FolderSupplier)
            outSup = (OutputSupplier) inSup;
        else
            outSup = getOutput(out);
        
        getLogger().lifecycle("remapping source...");
        applyRangeMap(inSup, outSup, srg, exc, rangemap, rangelog);
        
        
        inSup.close();
        outSup.close();
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
    
    private OutputSupplier getOutput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            return new ZipOutputSupplier(f);
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
    }
    
    private void applyRangeMap(InputSupplier inSup, OutputSupplier outSup, FileCollection srg, FileCollection exc, File rangeMap, File rangeLog) throws IOException
    {
        RangeApplier app = new RangeApplier().readSrg(srg.getFiles());
        
        final PrintStream debug = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
        final PrintStream stream = new PrintStream(rangeLog)
        {
            @Override
            public void println(String line)
            {
                debug.println(line);
                super.println(line);
            }
        };
        app.setOutLogger(stream);
        
        if (!exc.isEmpty())
        {
            app.readParamMap(exc);
        }
        
        // for debugging.
        app.dumpRenameMap();
        
        app.remapSources(inSup, outSup, rangeMap, false);
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
    
    @OutputFiles
    public FileCollection getOuts()
    {
        File outFile = getOut();
        if (outFile.isDirectory())
            return getProject().fileTree(outFile);
        else
            return getProject().files(outFile);
    }

    public File getOut()
    {
        return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }
    
    public FileCollection getSrgs()
    {
        return getProject().files(srg);
    }

    public void addSrg(DelayedFile srg)
    {
        this.srg.add(srg);
    }
    
    public void addSrg(String srg)
    {
        this.srg.add(srg);
    }
    
    public void addSrg(File srg)
    {
        this.srg.add(srg);
    }
    
    public FileCollection getExcs()
    {
        return getProject().files(exc);
    }
    
    public void addExc(DelayedFile exc)
    {
        this.exc.add(exc);
    }
    
    public void addExc(String exc)
    {
        this.exc.add(exc);
    }
    
    public void addExc(File exc)
    {
        this.exc.add(exc);
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

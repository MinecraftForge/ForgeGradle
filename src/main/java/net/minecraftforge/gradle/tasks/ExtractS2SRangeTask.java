package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.SequencedInputSupplier;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Lists;

public class ExtractS2SRangeTask extends DefaultTask
{
    @InputFiles
    private List<Object> libs = Lists.newArrayList();

    private final List<Object> sources = Lists.newArrayList();

    @OutputFile
    private Object rangeMap;

    @TaskAction
    public void doTask() throws IOException
    {
        List<File> ins = getSource();
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
            {
                ((SequencedInputSupplier) inSup).add(getInput(f));
            }
        }

        generateRangeMap(inSup, rangemap);
    }

    private void generateRangeMap(InputSupplier inSup, File rangeMap)
    {
        RangeExtractor extractor = new RangeExtractor();
        
        for (File f : getLibs())
        {
            //System.out.println("lib: "+f);
            extractor.addLibs(f);
        }
        
        extractor.setSrc(inSup);
        
        //extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);

        PrintStream stream = new PrintStream(Constants.getTaskLogStream(getProject(), this.getName() + ".log"));
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
        return getProject().file(rangeMap);
    }

    public void setRangeMap(Object out)
    {
        this.rangeMap = out;
    }

    @InputFiles
    public FileCollection getSources()
    {
        FileCollection collection = null;
        
        for (File f : getSource())
        {
            FileCollection col;
            if (f.isDirectory())
            {
                col = getProject().fileTree(f);
            }
            else
            {
                col = getProject().files(f);
            }
            
            if (collection == null)
                collection = col;
            else
                collection = collection.plus(col);
        }
        
        return collection;
    }

    public List<File> getSource()
    {
        List<File> files = new LinkedList<File>();
        for (Object o : sources)
            files.add(getProject().file(o));
        return files;
    }

    public void addSource(Object in)
    {
        this.sources.add(in);
    }

    public FileCollection getLibs()
    {
        FileCollection collection = null;
        
        for (Object o : libs)
        {
            FileCollection col;
            if (o instanceof FileCollection)
            {
                col = (FileCollection) o;
            }
            else
            {
                col = getProject().files(o);
            }
            
            if (collection == null)
                collection = col;
            else
                collection = collection.plus(col);
        }
        
        return collection;
    }

    public void addLibs(Object libs)
    {
        this.libs.add(libs);
    }
}

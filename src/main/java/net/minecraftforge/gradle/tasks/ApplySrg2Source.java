package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.RangeApplier;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.rangeapplier.RangeMap;
import net.minecraftforge.srg2source.rangeapplier.RenameMap;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;

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
    
    @InputFiles
    private FileCollection libs;
    
    private DelayedFile in;
    private DelayedFile out;
    private DelayedFile srg;
    
    @TaskAction
    public void doTask() throws IOException
    {
        File in = getIn();
        File out = this.out == null ? in : getOut();
        File rangemap = File.createTempFile("", "rangemap", this.getTemporaryDir());
        File srg = getSrg();
        
        InputSupplier inSup;
        OutputSupplier outSup;
        
        boolean isJar = in.getPath().endsWith(".jar") || in.getPath().endsWith(".zip");
        
        if (isJar)
        {
            // setup input
            inSup = new ZipInputSupplier();
            ((ZipInputSupplier) inSup).readZip(in);
            
            // setup output
            outSup = new ZipOutputSupplier(out);
        }
        else // folder!
        {
            @SuppressWarnings("resource")
            FolderSupplier fSup = new FolderSupplier(in);
            
            inSup = fSup;
            if (in == out)
                outSup = fSup;
            else
                outSup = new FolderSupplier(out);
        }
        
        generateRangeMap(inSup, rangemap);
        
        
        
        inSup.close();
        outSup.close();
    }
    
    public void generateRangeMap(InputSupplier inSup, File rangeMap)
    {
        RangeExtractor extractor = new RangeExtractor();
        extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);
        
        PrintStream stream = new PrintStream(Constants.createLogger(getLogger()));
        extractor.setOutLogger(stream);
        
        boolean worked = extractor.generateRangeMap(rangeMap);
        
        if (!worked)
            throw new RuntimeException("RangeMap generation Failed!!!");
    }
    
    public void applyRangeMap(InputSupplier inSup, OutputSupplier outSup, File srg, File rangemap)
    {
        // this API needs a  HUUUGE revamp.
//        RangeMap rangeMap = new RangeMap().read(rangemap);
//        RenameMap renameMap = new RenameMap();
//        renameMap.readSrg(new SrgContainer().readSrg(srg));
//        
//        for (String key : rangeMap.keySet())
//            RangeApplier.processJavaSourceFile(inSup, outSup, key, rangeMap.get(key), renameMap.maps, renameMap.imports, false, false, true);
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
        if (out == null)
            return getIn();
        else
            return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }

    public FileCollection getLibs()
    {
        return libs;
    }

    public void setLibs(FileCollection libs)
    {
        this.libs = libs;
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

package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;

public class ExtractTask extends DefaultTask
{
    private AntPathMatcher antMatcher = new AntPathMatcher();
    
    @InputFiles
    private LinkedHashSet<DelayedFile> sourcePaths = new LinkedHashSet<DelayedFile>();
    
    @Input
    private List<String> excludes = new LinkedList<String>();
    
    @Input
    private List<Closure<Boolean>> excludeCalls = new LinkedList<Closure<Boolean>>();
    
    @Input
    private List<String> includes = new LinkedList<String>();
    
    @Input
    private boolean includeEmptyDirs = true;
    
    @OutputDirectory
    private DelayedFile destDir = null;

    @TaskAction
    public void doTask() throws IOException
    {
        if (!destDir.call().exists())
        {
            destDir.call().mkdirs();
        }

        for (DelayedFile source : sourcePaths)
        {
            getLogger().debug("Extracting: " + source);

            ZipFile input = new ZipFile(source.call());
            try
            {
                Enumeration<? extends ZipEntry> itr = input.entries();
    
                while (itr.hasMoreElements())
                {
                    ZipEntry entry = itr.nextElement();
                    if (shouldExtract(entry.getName()))
                    {
                        File out = new File(destDir.call(), entry.getName());
                        getLogger().debug("  " + out);
                        if (entry.isDirectory())
                        {
                            if (includeEmptyDirs && !out.exists())
                            {
                                out.mkdirs();
                            }
                        }
                        else
                        {
                            File outParent = out.getParentFile();
                            if (!outParent.exists())
                            {
                                outParent.mkdirs();
                            }
    
                            FileOutputStream fos = new FileOutputStream(out);
                            InputStream ins = input.getInputStream(entry);
    
                            ByteStreams.copy(ins, fos);
    
                            fos.close();
                            ins.close();
                        }
                    }
                }
            }
            finally
            {
                input.close();
            }
        }
    }

    private boolean shouldExtract(String path)
    {
        for (String exclude : excludes)
        {
            if (antMatcher.matches(exclude, path))
            {
                return false;
            }
        }
        
        for (Closure<Boolean> exclude : excludeCalls)
        {
            if (exclude.call(path).booleanValue())
            {
                return false;
            }
        }

        for (String include : includes)
        {
            if (antMatcher.matches(include, path))
            {
                return true;
            }
        }

        return includes.size() == 0; //If it gets to here, then it matches nothing. default to true, if no includes were specified
    }

    public ExtractTask from(DelayedFile... paths)
    {
        for (DelayedFile path : paths)
        {
            sourcePaths.add(path);
        }
        return this;
    }

    public ExtractTask into(DelayedFile target)
    {
        destDir = target;
        return this;
    }
    
    public ExtractTask setDestinationDir(DelayedFile target)
    {
        destDir = target;
        return this;
    }
    
    public File getDestinationDir()
    {
        return destDir.call();
    }

    public ExtractTask include(String... paterns)
    {
        for (String patern : paterns)
        {
            includes.add(patern);
        }
        return this;
    }

    public ExtractTask exclude(String... paterns)
    {
        for (String patern : paterns)
        {
            excludes.add(patern);
        }
        return this;
    }
    
    public void exclude(Closure<Boolean> c)
    {
        excludeCalls.add(c);
    }
    
    public FileCollection getSourcePaths()
    {
        FileCollection collection = getProject().files(new Object[] {});
        
        for (DelayedFile file : sourcePaths)
            collection = collection.plus(getProject().files(file));
                
        return collection;
    }

    public boolean isIncludeEmptyDirs()
    {
        return includeEmptyDirs;
    }

    public void setIncludeEmptyDirs(boolean includeEmptyDirs)
    {
        this.includeEmptyDirs = includeEmptyDirs;
    }
}

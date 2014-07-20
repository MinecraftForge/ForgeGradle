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
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.*;

import com.google.common.io.ByteStreams;

public class ExtractTask extends CachedTask
{
    private final AntPathMatcher antMatcher = new AntPathMatcher();
    
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

    @Input
    @Optional
    private boolean clean = false;
    
    @Cached
    @OutputDirectory
    private DelayedFile destinationDir = null;

    @TaskAction
    public void doTask() throws IOException
    {
        File dest = destinationDir.call();

        if (shouldClean())
        {
            delete(dest);
        }

        dest.mkdirs();

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
                        File out = new File(dest, entry.getName());
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

    private void delete(File f) throws IOException
    {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        f.delete();
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
        destinationDir = target;
        return this;
    }
    
    public ExtractTask setDestinationDir(DelayedFile target)
    {
        destinationDir = target;
        return this;
    }
    
    public File getDestinationDir()
    {
        return destinationDir.call();
    }

    public List<String> getIncludes()
    {
        return includes;
    }
    
    public ExtractTask include(String... paterns)
    {
        for (String patern : paterns)
        {
            includes.add(patern);
        }
        return this;
    }
    
    public List<String> getExcludes()
    {
        return excludes;
    }

    public ExtractTask exclude(String... paterns)
    {
        for (String patern : paterns)
        {
            excludes.add(patern);
        }
        return this;
    }
    
    public List<Closure<Boolean>> getExcludeCalls()
    {
        return excludeCalls;
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
    
    @Override
    protected boolean defaultCache()
    {
        return false;
    }

    public boolean shouldClean()
    {
        return clean;
    }

    public void setClean(boolean clean)
    {
        this.clean = clean;
    }
}

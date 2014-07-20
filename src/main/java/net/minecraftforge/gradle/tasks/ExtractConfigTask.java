package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;

public class ExtractConfigTask extends CachedTask
{
    private final AntPathMatcher antMatcher = new AntPathMatcher();
    
    @Input
    private String config;
    
    @Input
    private List<String> excludes = new LinkedList<String>();
    
    @Input
    private List<Closure<Boolean>> excludeCalls = new LinkedList<Closure<Boolean>>();
    
    @Input
    private List<String> includes = new LinkedList<String>();
    
    @OutputDirectory
    private DelayedFile out;
    
    @TaskAction
    public void doTask() throws ZipException, IOException
    {
        File outDir = getOut();
        outDir.mkdirs();
        
        for (File source : getConfigFiles())
        {
            getLogger().debug("Extracting: " + source);

            ZipFile input = new ZipFile(source);
            try
            {
                Enumeration<? extends ZipEntry> itr = input.entries();
    
                while (itr.hasMoreElements())
                {
                    ZipEntry entry = itr.nextElement();
                    if (shouldExtract(entry.getName()))
                    {
                        File outFile = new File(outDir, entry.getName());
                        getLogger().debug("  " + outFile);
                        if (!entry.isDirectory())
                        {
                            File outParent = outFile.getParentFile();
                            if (!outParent.exists())
                            {
                                outParent.mkdirs();
                            }
    
                            FileOutputStream fos = new FileOutputStream(outFile);
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

    public String getConfig()
    {
        return config;
    }

    public void setConfig(String config)
    {
        this.config = config;
    }
    
    @Optional
    @InputFiles
    public FileCollection getConfigFiles()
    {
        return getProject().getConfigurations().getByName(config);
    }

    public File getOut()
    {
        return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }
    
    public List<String> getIncludes()
    {
        return includes;
    }
    
    public ExtractConfigTask include(String... paterns)
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

    public ExtractConfigTask exclude(String... paterns)
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
    
    @Override
    protected boolean defaultCache()
    {
        return false;
    }
}

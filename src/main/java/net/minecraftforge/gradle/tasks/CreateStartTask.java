package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class CreateStartTask extends CachedTask
{
    @Input
    HashMap<String, String> resources = Maps.newHashMap();
    
    @Input
    HashMap<String, Object> replacements = Maps.newHashMap();
    
    @Cached
    @OutputDirectory
    private DelayedFile startOut;
    
    private String classpath;
    private boolean compile;
    
    @TaskAction
    public void doStuff() throws IOException
    {
        // resolve the replacements
        for (Entry<String, Object> entry : replacements.entrySet())
        {
            replacements.put(entry.getKey(), resolveString(entry.getValue()));
        }
        
        // set the output of the files
        File resourceDir = compile ? new File(getTemporaryDir(), "extracted") : getStartOut();
        
        // replace and extract
        for (Entry<String, String> resEntry : resources.entrySet())
        {
            String out = resEntry.getValue();
            for (Entry<String, Object> replacement : replacements.entrySet())
            {
                out = out.replace(replacement.getKey(), (String)replacement.getValue());
            }
            
            // write file
            File outFile = new File(resourceDir, resEntry.getKey());
            outFile.getParentFile().mkdirs();
            Files.write(out, outFile, Charsets.UTF_8);
        }
        
        // now compile, if im compiling.
        if (compile)
        {
            File compiled = getStartOut();
            compiled.mkdirs();

            this.getAnt().invokeMethod("javac", ImmutableMap.builder()
                    .put("srcDir", resourceDir.getCanonicalPath())
                    .put("destDir", compiled.getCanonicalPath())
                    .put("failonerror", true)
                    .put("includeantruntime", false)
                    .put("classpath", getProject().getConfigurations().getByName(classpath).getAsPath())
                    .put("encoding", "utf-8")
                    .put("source", "1.6")
                    .put("target", "1.6")
                    .build());
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    private String resolveString(Object obj) throws IOException
    {
        if (obj instanceof Closure)
            return resolveString(((Closure)obj).call());
        else if (obj instanceof File)
            return ((File)obj).getCanonicalPath().replace('\\', '/');
        else
            return obj.toString();
    }

    private String getResource(URL resource)
    {
        try
        {
            return Resources.toString(resource, Charsets.UTF_8);
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
            return "";
        }
    }
    
    /**
     * Use Resources.getResource() for this
     * @param resource resource URL
     * @param outName name of the resource after its been extracted
     */
    public void addResource(URL resource, String outName)
    {
        resources.put(outName, getResource(resource));
    }
    
    public void removeResource(String key)
    {
        resources.remove(key);
    }
    
    public void addResource(String resource, String outName)
    {
        this.addResource(Constants.getResource(resource), outName);
    }
    
    public void addResource(String thing)
    {
        this.addResource(thing, thing);
    }
    
    public void addReplacement(String token, Object replacement)
    {
        replacements.put(token, replacement);
    }
    
    public void compileResources(String classpathConfig)
    {
        compile = true;
        classpath = classpathConfig;
    }
    
    public File getStartOut()
    {
        File dir = startOut.call();
        if (!dir.exists())
            dir.mkdirs();
        return startOut.call();
    }

    public void setStartOut(DelayedFile outputFile)
    {
        this.startOut = outputFile;
    }
}

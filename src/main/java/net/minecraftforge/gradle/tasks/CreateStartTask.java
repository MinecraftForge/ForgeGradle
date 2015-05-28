package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class CreateStartTask extends CachedTask
{
    @Input
    HashMap<String, String> resources    = Maps.newHashMap();

    @Input
    HashMap<String, Object> replacements = Maps.newHashMap();

    @Cached
    @OutputDirectory
    private Object          startOut;

    private Set<String>    classpath    = Sets.newHashSet();
    private boolean         compile;

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
                out = out.replace(replacement.getKey(), (String) replacement.getValue());
            }

            // write file
            File outFile = new File(resourceDir, resEntry.getKey());
            outFile.getParentFile().mkdirs();
            Files.write(out, outFile, Charsets.UTF_8);
        }

        // now compile, if im compiling.
        if (compile)
        {
            File compiled = getStartOut(); // quas
            compiled.mkdirs(); // wex
            
            // build claspath    exort
            FileCollection col = null;
            for (String s : classpath)
            {
                FileCollection config = getProject().getConfigurations().getByName(s);
                
                if (col == null)
                    col = config;
                else
                    col = col.plus(config);
            }

            // INVOKE!
            this.getAnt().invokeMethod("javac", ImmutableMap.builder()
                    .put("srcDir", resourceDir.getCanonicalPath())
                    .put("destDir", compiled.getCanonicalPath())
                    .put("failonerror", true)
                    .put("includeantruntime", false)
                    .put("classpath", col) // because ant knows what a file collection is
                    .put("encoding", "utf-8")
                    .put("source", "1.6")
                    .put("target", "1.6")
                    .put("compilerarg", "-Xlint:-options") // to silence the bootstrap classpath warning
                    .build());
        }

    }

    @SuppressWarnings("rawtypes")
    private String resolveString(Object obj) throws IOException
    {
        if (obj instanceof Closure)
            return resolveString(((Closure) obj).call());
        else if (obj instanceof File)
            return ((File) obj).getCanonicalPath().replace('\\', '/');
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
     * @param resource URL of the resource in the jar
     * @param outName name of the resource once extracted
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

    public void addClasspathConfig(String classpathConfig)
    {
        compile = true;
        classpath.add(classpathConfig);
    }

    public File getStartOut()
    {
        File dir = getProject().file(startOut);
        if (!dir.exists())
            dir.mkdirs();
        return dir;
    }

    public void setStartOut(Object outputFile)
    {
        this.startOut = outputFile;
    }
}

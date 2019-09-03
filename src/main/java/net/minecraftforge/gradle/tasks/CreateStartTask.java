/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.AntBuilder;
import org.gradle.api.Task;
import org.gradle.api.AntBuilder.AntMessagePriority;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.GradleVersion;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class CreateStartTask extends CachedTask
{
    @Input
    HashMap<String, String>     resources    = Maps.newHashMap();

    @Input
    HashMap<String, Object>     replacements = Maps.newHashMap();

    @Input
    List<String>                extraLines   = Lists.newArrayList();

    @Cached
    @OutputDirectory
    private Object              startOut;

    private Set<String>         classpath    = Sets.newHashSet();
    private boolean             compile;

    private static final String EXTRA_LINES  = "//@@EXTRALINES@@";

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

            // replace extra lines
            if (!extraLines.isEmpty())
            {
                String replacement = Joiner.on('\n').join(extraLines);
                out = out.replace(EXTRA_LINES, replacement);
            }

            // write file
            File outFile = new File(resourceDir, resEntry.getKey());
            outFile.getParentFile().mkdirs();
            Files.write(out, outFile, Charsets.UTF_8);
        }

        // now compile, if im compiling.
        if (compile)
        {
            final File compiled = getStartOut(); // quas
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

            AntBuilder ant = CreateStartTask.setupAnt(this);
            // INVOKE!
            ant.invokeMethod("javac", ImmutableMap.builder()
                    .put("srcDir", resourceDir.getCanonicalPath())
                    .put("destDir", compiled.getCanonicalPath())
                    .put("failonerror", true)
                    .put("includeantruntime", false)
                    .put("classpath", col.getAsPath()) // because ant knows what a file collection is
                    .put("encoding", "utf-8")
                    .put("source", "1.8")
                    .put("target", "1.8")
                    .put("debug", "true")
                    .build());

            // copy the sources too, for debugging through GradleStart
            getProject().fileTree(resourceDir).visit(new FileVisitor() {

                @Override
                public void visitDir(FileVisitDetails arg0)
                {
                    // ignore
                }

                @Override
                public void visitFile(FileVisitDetails arg0)
                {
                    arg0.copyTo(arg0.getRelativePath().getFile(compiled));
                }

            });
        }

    }

    public static AntBuilder setupAnt(Task task)
    {
        AntBuilder ant = task.getAnt();
        LogLevel startLevel = task.getProject().getGradle().getStartParameter().getLogLevel();
        if (startLevel.compareTo(LogLevel.LIFECYCLE) >= 0)
        {
            GradleVersion v2_14 = GradleVersion.version("2.14");
            if (GradleVersion.current().compareTo(v2_14) >= 0)
            {
                ant.setLifecycleLogLevel(AntMessagePriority.ERROR);
            }
            else
            {
                try {
                    LoggingManager.class.getMethod("setLevel", LogLevel.class).invoke(task.getLogging(), LogLevel.ERROR);
                } catch (Exception e) {
                    //Couldn't find it? We are on some weird version oh well.
                    task.getLogger().info("Could not set log level:", e);
                }
            }
        }
        return ant;
    }

    @SuppressWarnings("rawtypes")
    private String resolveString(Object obj) throws IOException
    {
        if (obj == null)
            return null;

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
        catch (IOException e)
        {
            throw new RuntimeException(e);
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

    public void addExtraLine(String extra)
    {
        this.extraLines.add(extra);
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

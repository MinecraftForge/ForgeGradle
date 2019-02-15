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
package net.minecraftforge.gradle.tasks.fernflower;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.gradle.process.JavaExecSpec;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

public class ApplyFernFlowerTask extends CachedTask {

    // 2.5 GB
    private static final long REQUIRED_MEMORY = (long) (2.5 * 1024 * 1024 * 1024);
    private static final String FORK_FLAG = "forkDecompile";

    @InputFile
    Object inJar;

    @Cached
    @OutputFile
    Object outJar;

    private FileCollection classpath;
    private FileCollection forkedClasspath;

    @TaskAction
    public void applyFernFlower() throws IOException
    {
        final File in = getInJar();
        final File out = getOutJar();

        final File tempDir = this.getTemporaryDir();
        final File tempJar = new File(this.getTemporaryDir(), in.getName());

        Map<String, Object> mapOptions = new HashMap<String, Object>();
        mapOptions.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
        mapOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        mapOptions.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1");
        mapOptions.put(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, "1");
        mapOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        mapOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        mapOptions.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
        mapOptions.put(IFernflowerPreferences.LITERALS_AS_IS, "0");
        mapOptions.put(IFernflowerPreferences.UNIT_TEST_MODE, "0");
        mapOptions.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "0");
        mapOptions.put(DecompilerContext.RENAMER_FACTORY, AdvancedJadRenamerFactory.class.getName());

        FernFlowerSettings settings = new FernFlowerSettings(tempDir, in, tempJar, Constants.getTaskLogFile(getProject(), getName() + ".log"), classpath.getFiles(), mapOptions);

        runFernFlower(settings);

        Constants.copyFile(tempJar, out);
    }

    private void runFernFlower(FernFlowerSettings settings) throws IOException
    {
        // forking allowed if the property is not present or it is "true" ("true" is the default)
        boolean forkAllowed = !getProject().hasProperty(FORK_FLAG) || Boolean.parseBoolean(getProject().property(FORK_FLAG).toString());
        if (!forkAllowed || Runtime.getRuntime().maxMemory() >= REQUIRED_MEMORY) {
            // no fork, either not allowed or memory is OK
            FernFlowerInvoker.runFernFlower(settings);
        } else {
            // put this in the info logs, but day-to-day use doesn't need to see it
            getLogger().info("Note: " + Constants.GROUP_FG + " is forking a new process to run decompilation.");
            getLogger().debug("Settings: {}", settings);
            final File data = File.createTempFile("fg-fernflowersettings", ".ser");
            try {
                writeSettings(settings, data);
                runForkedFernFlower(data);
            } finally {
                data.delete();
            }
        }
    }

    @SuppressWarnings("serial")
    private void writeSettings(final FernFlowerSettings settings, File data) throws IOException
    {
        ResourceGroovyMethods.withObjectOutputStream(data, new Closure<Void>(this, this) {

            @Override
            public Void call(Object... args)
            {
                ObjectOutputStream out = (ObjectOutputStream) args[0];
                try {
                    out.writeObject(settings);
                } catch (IOException e) {
                    throwRuntimeException(e);
                }
                return null;
            }
        });
    }

    private void runForkedFernFlower(final File data)
    {
        ExecResult result = getProject().javaexec(new Action<JavaExecSpec>() {

            @Override
            public void execute(JavaExecSpec exec)
            {
                exec.classpath(forkedClasspath);
                exec.setMain(FernFlowerInvoker.class.getName());
                exec.setJvmArgs(ImmutableList.of("-Xmx3G"));
                // pass the temporary file
                exec.args(data);

                // Forward std streams
                exec.setStandardOutput(System.out);
                exec.setErrorOutput(System.err);
            }
        });
        result.rethrowFailure();
        result.assertNormalExitValue();
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }

    public FileCollection getClasspath()
    {
        return classpath;
    }

    public void setClasspath(FileCollection classpath)
    {
        this.classpath = classpath;
    }

    public FileCollection getForkedClasspath()
    {
        return forkedClasspath;
    }

    public void setForkedClasspath(FileCollection forkedClasspath)
    {
        this.forkedClasspath = forkedClasspath;
    }

}

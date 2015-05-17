package net.minecraftforge.gradle.tasks.user;

import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.RemapperProcessor;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public class SingleDeobfTask extends CachedTask
{
    @InputFile
    private DelayedFile  inJar;

    @InputFile
    private DelayedFile  srg;

    // getter is marked for input files
    private List<Object> classpath = new ArrayList<Object>(5);

    @Cached
    @OutputFile
    private DelayedFile  outJar;

    @TaskAction
    public void doTask() throws IOException
    {
        File in = getInJar();
        File out = getOutJar();
        File mappings = getSrg();

        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(mappings);

        // make a processor out of the ATS and mappings.
        RemapperProcessor srgProcessor = new RemapperProcessor(null, mapping, null);

        // make remapper
        JarRemapper remapper = new JarRemapper(srgProcessor, mapping, null);

        // load jar
        Jar input = Jar.init(in);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));
        inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(ObfuscateTask.toUrls(getClasspath()))));
        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        // remap jar
        remapper.remapJar(input, out);
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }

    @InputFiles
    public FileCollection getClasspath()
    {
        return getProject().files(classpath.toArray());
    }

    /**
     * Whatever works, Closure, file, dir, dependency config.
     * Evaluated with project.file later
     * @param classpathEntry entry
     */
    public void addClasspath(Object classpathEntry)
    {
        classpath.add(classpathEntry);
    }
}

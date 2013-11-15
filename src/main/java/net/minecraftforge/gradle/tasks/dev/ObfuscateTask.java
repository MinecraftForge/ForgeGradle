package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.dev.FmlDevPlugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

public class ObfuscateTask extends DefaultTask
{
    private DelayedFile outJar;
    private DelayedFile srg;
    private boolean     reverse;
    private DelayedFile buildFile;

    @TaskAction
    public void doTask() throws IOException
    {
        getLogger().debug("Building child project model...");
        Project childProj = FmlDevPlugin.getProject(getBuildFile(), getProject());
        AbstractTask compileTask = (AbstractTask) childProj.getTasks().getByName("compileJava");
        AbstractTask jarTask = (AbstractTask) childProj.getTasks().getByName("jar");

        // executing jar task
        getLogger().debug("Executing child Jar task...");
        executeTask(jarTask);

        getLogger().debug("Obfuscating jar...");
        obfuscate((File)jarTask.property("archivePath"), (FileCollection)compileTask.property("classpath"));
    }
    
    private void executeTask(AbstractTask task)
    {
        for (Object dep : task.getTaskDependencies().getDependencies(task))
        {
            executeTask((AbstractTask) dep);
        }

        if (!task.getState().getExecuted())
        {
            getLogger().lifecycle(task.getPath());
            task.execute();
        }
    }

    private void obfuscate(File inJar, FileCollection classpath) throws FileNotFoundException, IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(Files.newReader(getSrg(), Charset.defaultCharset()), null, null, reverse);

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping);

        // load jar
        Jar input = Jar.init(inJar);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));

        if (classpath != null)
            inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(toUrls(classpath))));

        mapping.setFallbackInheritanceProvider(inheritanceProviders);
        
        File out = getOutJar();
        if (!out.getParentFile().exists()) //Needed because SS doesn't create it.
        {
            out.getParentFile().mkdirs();
        }

        // remap jar
        remapper.remapJar(input, getOutJar());
    }
    
    public static URL[] toUrls(FileCollection collection) throws MalformedURLException
    {
        ArrayList<URL> urls = new ArrayList<URL>();
        
        for (File file : collection.getFiles())
            urls.add(file.toURI().toURL());
        
        return urls.toArray(new URL[urls.size()]);
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

    public boolean isReverse()
    {
        return reverse;
    }

    public void setReverse(boolean reverse)
    {
        this.reverse = reverse;
    }

    public File getBuildFile()
    {
        return buildFile.call();
    }

    public void setBuildFile(DelayedFile buildFile)
    {
        this.buildFile = buildFile;
    }
}

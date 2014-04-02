package net.minecraftforge.gradle.tasks.dev;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.dev.FmlDevPlugin;
import net.minecraftforge.gradle.extrastuff.ReobfExceptor;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

public class ObfuscateTask extends DefaultTask
{
    private DelayedFile outJar;
    private DelayedFile preFFJar;
    private DelayedFile srg;
    private DelayedFile exc;
    private boolean     reverse;
    private DelayedFile buildFile;
    private LinkedList<Action<Project>> configureProject = new LinkedList<Action<Project>>();
    private DelayedFile methodsCsv;
    private DelayedFile fieldsCsv;
    private String subTask = "jar";
    private LinkedList<String> extraSrg = new LinkedList<String>();

    @TaskAction
    public void doTask() throws IOException
    {
        getLogger().debug("Building child project model...");
        Project childProj = FmlDevPlugin.getProject(getBuildFile(), getProject());
        for (Action<Project> act : configureProject)
        {
            if (act != null)
                act.execute(childProj);
        }
        
        AbstractTask compileTask = (AbstractTask) childProj.getTasks().getByName("compileJava");
        AbstractTask jarTask = (AbstractTask) childProj.getTasks().getByName(subTask);

        // executing jar task
        getLogger().debug("Executing child "+subTask+" task...");
        executeTask(jarTask);
        
        File inJar = (File)jarTask.property("archivePath");

        File srg = getSrg();

        if (getExc() != null)
        {
            ReobfExceptor exceptor = new ReobfExceptor();
            exceptor.toReobfJar = inJar;
            exceptor.deobfJar = getPreFFJar();
            exceptor.excConfig = getExc();
            exceptor.fieldCSV = getFieldsCsv();
            exceptor.methodCSV = getMethodsCsv();
            
            File outSrg =  new File(this.getTemporaryDir(), "reobf_cls.srg");
            
            exceptor.doFirstThings();
            exceptor.buildSrg(srg, outSrg);
            
            srg = outSrg;
        }
        
        // append SRG
        BufferedWriter writer = new BufferedWriter(new FileWriter(srg, true));
        for (String line : extraSrg)
        {
            writer.write(line);
            writer.newLine();
        }
        writer.flush();
        writer.close();

        getLogger().debug("Obfuscating jar...");
        obfuscate(inJar, (FileCollection)compileTask.property("classpath"), srg);
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

    private void obfuscate(File inJar, FileCollection classpath, File srg) throws FileNotFoundException, IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(Files.newReader(srg, Charset.defaultCharset()), null, null, reverse);

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
    
    public File getPreFFJar()
    {
        return preFFJar.call();
    }

    public void setPreFFJar(DelayedFile preFFJar)
    {
        this.preFFJar = preFFJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }

    public File getExc()
    {
        return exc.call();
    }

    public void setExc(DelayedFile exc)
    {
        this.exc = exc;
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


    public File getMethodsCsv()
    {
        return methodsCsv.call();
    }

    public void setMethodsCsv(DelayedFile methodsCsv)
    {
        this.methodsCsv = methodsCsv;
    }

    public File getFieldsCsv()
    {
        return fieldsCsv.call();
    }

    public void setFieldsCsv(DelayedFile fieldsCsv)
    {
        this.fieldsCsv = fieldsCsv;
    }
    
    public void configureProject(Action<Project> action)
    {
        configureProject.add(action);
    }

    public LinkedList<String> getExtraSrg()
    {
        return extraSrg;
    }

    public void setExtraSrg(LinkedList<String> extraSrg)
    {
        this.extraSrg = extraSrg;
    }

    public String getSubTask()
    {
        return subTask;
    }

    public void setSubTask(String subTask)
    {
        this.subTask = subTask;
    }
}

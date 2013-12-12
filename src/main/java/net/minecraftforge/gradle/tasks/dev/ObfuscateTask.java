package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import de.oceanlabs.mcp.mcinjector.StringUtil;

public class ObfuscateTask extends DefaultTask
{
    private DelayedFile outJar;
    private DelayedFile srg;
    private DelayedFile exc;
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
        
        File srg = getSrg();

        if (getExc() != null)
        {
            srg = createSrg(srg, getExc());
        }

        getLogger().debug("Obfuscating jar...");
        obfuscate((File)jarTask.property("archivePath"), (FileCollection)compileTask.property("classpath"), srg);
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

    private File createSrg(File base, File exc) throws IOException
    {
        File srg = new File(this.getTemporaryDir(), "reobf_cls.srg");
        if (srg.isFile())
            srg.delete();
        
        Map<String, String> map = Files.readLines(exc, Charset.defaultCharset(), new LineProcessor<Map<String, String>>()
        {
            Map<String, String> tmp = Maps.newHashMap();
            
            @Override
            public boolean processLine(String line) throws IOException
            {
                if (line.contains(".") ||
                   !line.contains("=") ||
                    line.startsWith("#")) return true;

                String[] s = line.split("=");
                tmp.put(s[0], s[1] + "_");

                return true;
            }

            @Override
            public Map<String, String> getResult()
            {
                return tmp;
            }
        });

        String fixed = Files.readLines(base, Charset.defaultCharset(), new SrgLineProcessor(map));
        Files.write(fixed.getBytes(), srg);
        return srg;
    }

    private static class SrgLineProcessor implements LineProcessor<String>
    {
        Map<String, String> map;
        StringBuilder out = new StringBuilder();
        Pattern reg = Pattern.compile("L([^;]+);");

        private SrgLineProcessor(Map<String, String> map)
        {
            this.map = map;
        }

        private String rename(String cls)
        {
            String rename = map.get(cls);
            return rename == null ? cls : rename;
        }

        private String[] rsplit(String value, String delim)
        {
            int idx = value.lastIndexOf(delim);
            return new String[]
            {
                value.substring(0, idx),
                value.substring(idx + 1)
            };
        }

        @Override
        public boolean processLine(String line) throws IOException
        {
            String[] split = line.split(" ");
            if (split[0].equals("CL:"))
            {
                split[2] = rename(split[2]);
            }
            else if (split[0].equals("FD:"))
            {
                String[] s = rsplit(split[2], "/");
                split[2] = rename(s[0] + "/" + s[1]);
            }
            else if (split[0].equals("MD:"))
            {
                String[] s = rsplit(split[2], "/");
                split[2] = rename(s[0] + "/" + s[1]);

                Matcher m = reg.matcher(split[3]);
                StringBuffer b = new StringBuffer();
                while(m.find())
                {
                    m.appendReplacement(b, "L" + rename(m.group(1)) + ";");
                }
                m.appendTail(b);
                split[3] = b.toString();
            }
            out.append(StringUtil.joinString(Arrays.asList(split), " ")).append('\n');
            return true;
        }

        @Override
        public String getResult()
        {
            return out.toString();
        }
        
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
}

package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.util.ZipFileTree;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class TaskRecompileMc extends CachedTask
{
    @InputFile
    private Object inSources;

    @Optional
    @InputFile
    private Object inResources;

    @Input
    private String classpath;

    @Cached
    @OutputFile
    private Object outJar;

    @TaskAction
    public void doStuff() throws IOException
    {
        File inJar = getInSources();
        File tempSrc = new File(getTemporaryDir(), "sources");
        File tempCls = new File(getTemporaryDir(), "compiled");
        File outJar = getOutJar();

        tempSrc.mkdirs();
        extractSources(tempSrc, inJar);

        // recompile
        // now compile, if im compiling.
        tempCls.mkdirs();

        this.getAnt().invokeMethod("javac", ImmutableMap.builder()
                .put("srcDir", tempSrc.getCanonicalPath())
                .put("destDir", tempCls.getCanonicalPath())
                .put("failonerror", true)
                .put("includeantruntime", false)
                .put("classpath", getProject().getConfigurations().getByName(classpath).getAsPath())
                .put("encoding", "utf-8")
                .put("source", "1.6")
                .put("target", "1.6")
                //.put("compilerarg", "-Xlint:-options") // to silence the bootstrap classpath warning
                .build());

        outJar.getParentFile().mkdirs();
        createOutput(outJar, inJar, tempCls, getInResources());
    }

    private static void extractSources(File tempDir, File inJar) throws IOException
    {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(inJar));
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {
            // we dont care about directories.. we can make em later when needed
            // we only want java files to compile too, can grab the other resources from the jar later
            if (entry.isDirectory() || !entry.getName().endsWith(".java"))
                continue;

            File out = new File(tempDir, entry.getName());
            out.getParentFile().mkdirs();
            Files.asByteSink(out).writeFrom(zin);
        }

        zin.close();
    }

    private void createOutput(File outJar, File sourceJar, File classDir, File resourceJar) throws IOException
    {
        Set<String> elementsAdded = Sets.newHashSet();

        // make output
        JarOutputStream zout = new JarOutputStream(new FileOutputStream(outJar));

        Visitor visitor = new Visitor(zout, elementsAdded);
        
        // custom resources should override existing ones, so resources first.
        if (resourceJar != null)
        {
            new ZipFileTree(resourceJar).visit(visitor);
        }

        new ZipFileTree(sourceJar).visit(visitor); // then the ones from the the original sources
        getProject().fileTree(classDir).visit(visitor); // then the classes

        zout.close();
    }

    private static final class Visitor implements FileVisitor
    {
        private final ZipOutputStream zout;
        private final Set<String>     entries;

        public Visitor(ZipOutputStream zout, Set<String> entries)
        {
            this.zout = zout;
            this.entries = entries;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails)
        {
        }

        @Override
        public void visitFile(FileVisitDetails file)
        {
            try
            {
                String name = file.getRelativePath().toString().replace('\\', '/');

                if (entries.contains(name) || name.endsWith(".java"))
                    return;

                entries.add(name);
                zout.putNextEntry(new ZipEntry(name));

                InputStream stream = file.open();
                ByteStreams.copy(file.open(), zout);
                stream.close();

            }
            catch (IOException e)
            {
                Throwables.propagate(e);
            }
        }
    }

    public File getInSources()
    {
        return getProject().file(inSources);
    }

    public void setInSources(Object inSources)
    {
        this.inSources = inSources;
    }

    public File getInResources()
    {
        if (inResources == null)
            return null;
        else
            return getProject().file(inResources);
    }

    public void setInResources(Object inResources)
    {
        this.inResources = inResources;
    }

    public String getClasspath()
    {
        return classpath;
    }

    public void setClasspath(String classpath)
    {
        this.classpath = classpath;
    }

    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }
}

package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import net.minecraftforge.gradle.util.ZipFileTree;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import com.google.common.base.Throwables;

public class SplitJarTask extends CachedTask implements PatternFilterable
{
    @InputFile
    private Object     inJar;

    @Input
    private PatternSet pattern = new PatternSet();

    @Cached
    @OutputFile
    private Object     outFirst;

    @Cached
    @OutputFile
    private Object     outSecond;

    @TaskAction
    public void doTask() throws IOException
    {
        // get the spec
        final Spec<FileTreeElement> spec = pattern.getAsSpec();

        File input = getInJar();

        File out1 = getOutFirst();
        File out2 = getOutSecond();

        out1.getParentFile().mkdirs();
        out2.getParentFile().mkdirs();

        // begin reading jar
        final JarOutputStream zout1 = new JarOutputStream(new FileOutputStream(out1));
        final JarOutputStream zout2 = new JarOutputStream(new FileOutputStream(out2));

        (new ZipFileTree(input)).visit(new FileVisitor() {

            @Override
            public void visitDir(FileVisitDetails details)
            {
                JarEntry entry = new JarEntry(details.getPath());
                entry.setSize(details.getSize());
                entry.setTime(details.getLastModified());

                try
                {
                    zout1.putNextEntry(entry);
                    details.copyTo(zout1);
                    zout1.closeEntry();

                    zout2.putNextEntry(entry);
                    details.copyTo(zout2);
                    zout2.closeEntry();
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
            }

            @Override
            public void visitFile(FileVisitDetails details)
            {
                JarEntry entry = new JarEntry(details.getPath());
                entry.setSize(details.getSize());
                entry.setTime(details.getLastModified());

                try
                {
                    if (spec.isSatisfiedBy(details))
                    {
                        zout1.putNextEntry(entry);
                        details.copyTo(zout1);
                        zout1.closeEntry();
                    }
                    else
                    {
                        zout2.putNextEntry(entry);
                        details.copyTo(zout2);
                        zout2.closeEntry();
                    }
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
            }
        });

        zout1.close();
        zout2.close();
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutFirst()
    {
        return getProject().file(outFirst);
    }

    public void setOutFirst(Object outFirst)
    {
        this.outFirst = outFirst;
    }

    public File getOutSecond()
    {
        return getProject().file(outSecond);
    }

    public void setOutSecond(Object outSecond)
    {
        this.outSecond = outSecond;
    }

    @Override
    public PatternFilterable exclude(String... arg0)
    {
        return pattern.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Iterable<String> arg0)
    {
        return pattern.exclude(arg0);
    }

    @Override
    public PatternFilterable exclude(Spec<FileTreeElement> arg0)
    {
        return pattern.exclude(arg0);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PatternFilterable exclude(Closure arg0)
    {
        return pattern.exclude(arg0);
    }

    @Override
    public Set<String> getExcludes()
    {
        return pattern.getExcludes();
    }

    @Override
    public Set<String> getIncludes()
    {
        return pattern.getIncludes();
    }

    @Override
    public PatternFilterable include(String... arg0)
    {
        return pattern.include(arg0);
    }

    @Override
    public PatternFilterable include(Iterable<String> arg0)
    {
        return pattern.include(arg0);
    }

    @Override
    public PatternFilterable include(Spec<FileTreeElement> arg0)
    {
        return pattern.include(arg0);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PatternFilterable include(Closure arg0)
    {
        return pattern.include(arg0);
    }

    @Override
    public PatternFilterable setExcludes(Iterable<String> arg0)
    {
        return pattern.setExcludes(arg0);
    }

    @Override
    public PatternFilterable setIncludes(Iterable<String> arg0)
    {
        return pattern.setIncludes(arg0);
    }
}

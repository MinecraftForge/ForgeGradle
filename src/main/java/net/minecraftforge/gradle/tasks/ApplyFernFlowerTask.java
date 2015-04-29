package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaExecSpec;

public class ApplyFernFlowerTask extends CachedTask
{
    @InputFile
    Object fernflower;
    
    @InputFile
    Object inJar;
    
    @Cached
    @OutputFile
    Object outJar;
    
    @TaskAction
    public void applyFernFLower() throws IOException
    {
        final File in = getInJar();
        final File out = getOutJar();
        final File ff = getFernflower();
        
        final File tempDir = this.getTemporaryDir();
        final File tempJar = new File(this.getTemporaryDir(), in.getName());
        
        getProject().javaexec(new Closure<JavaExecSpec>(this)
        {
            private static final long serialVersionUID = 4608694547855396167L;

            public JavaExecSpec call()
            {
                JavaExecSpec exec = (JavaExecSpec) getDelegate();

                exec.args(
                        ff.getAbsolutePath(),
                        "-din=1",
                        "-rbr=0",
                        "-dgs=1",
                        "-asc=1",
                        "-log=ERROR",
                        in.getAbsolutePath(),
                        tempDir.getAbsolutePath()
                );

                exec.setMain("-jar");
                exec.setWorkingDir(ff.getParentFile());

                exec.classpath(Constants.getClassPath());
                exec.setStandardOutput(Constants.getTaskLogStream(getProject(), getName() + ".log"));

                exec.setMaxHeapSize("512M");

                return exec;
            }

            public JavaExecSpec call(Object obj)
            {
                return call();
            }
        });
        
        Constants.copyFile(tempJar, out);
    }

    public File getFernflower()
    {
        return getProject().file(fernflower);
    }

    public void setFernflower(Object fernflower)
    {
        this.fernflower = fernflower;
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
}

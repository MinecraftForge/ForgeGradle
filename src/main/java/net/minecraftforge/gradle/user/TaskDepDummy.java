package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;

@ParallelizableTask
public class TaskDepDummy extends DefaultTask
{
    private Object outputFile;
    
    @TaskAction
    public void makeEmptyJar() throws IOException
    {
        File out = getOutputFile();
        out.getParentFile().mkdirs();
        
        // yup.. a dummy jar....
        JarOutputStream stream = new JarOutputStream(new FileOutputStream(out));
        stream.putNextEntry(new JarEntry("dummyThing"));
        stream.write(0xffffffff);
        stream.closeEntry();
        stream.close();
    }

    public File getOutputFile()
    {
        return getProject().file(outputFile);
    }

    public void setOutputFile(Object outputFile)
    {
        this.outputFile = outputFile;
    }
}

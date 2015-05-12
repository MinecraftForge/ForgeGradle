package net.minecraftforge.gradle.patcher;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import lzma.streams.LzmaOutputStream;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;

class TaskCompressLZMA extends CachedTask
{
    @InputFile
    private Object inputFile;

    @Cached
    @OutputFile
    private Object outputFile;

    //@formatter:off
    public TaskCompressLZMA() { }
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException
    {
        final BufferedInputStream in = new BufferedInputStream(new FileInputStream(getInputFile()));
        final OutputStream out = new LzmaOutputStream.Builder(new FileOutputStream(getOutputFile()))
                .useEndMarkerMode(true)
                .build();

        ByteStreams.copy(in, out);

        in.close();
        out.close();
    }

    public File getInputFile()
    {
        return getProject().file(inputFile);
    }

    public void setInputFile(DelayedFile inputFile)
    {
        this.inputFile = inputFile;

        if (outputFile == null)
        {
            outputFile = inputFile;
        }
    }

    public File getOutputFile()
    {
        return getProject().file(outputFile);
    }

    public void setOutputFile(DelayedFile outputFile)
    {
        this.outputFile = outputFile;
    }
}

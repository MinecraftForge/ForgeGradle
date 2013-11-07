package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;
import groovy.util.MapEntry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class FileFilterTask extends DefaultTask
{
    @InputFile
    DelayedFile inputFile;

    @OutputFile
    DelayedFile outputFile;

    ArrayList<MapEntry> replacements = new ArrayList<MapEntry>();

    public FileFilterTask()
    {
        this.getOutputs().upToDateWhen(Constants.CALL_FALSE);
    }
    
    @TaskAction
    public void doTask() throws IOException
    {
        String input = Files.toString(getInputFile(), Charsets.UTF_8);

        for (MapEntry e : replacements)
        {
            input = input.replaceAll(toString(e.getKey()), toString(e.getValue()));
        }

        Files.write(input.getBytes(Charsets.UTF_8), getOutputFile());
    }

    @SuppressWarnings("unchecked")
    private String toString(Object obj)
    {
        if (obj instanceof Closure)
        {
            return ((Closure<String>)obj).call();
        }
        else
        {
            return (String)obj;
        }
    }

    public void addReplacement(Object search, Object replace)
    {
        replacements.add(new MapEntry(search, replace));
    }

    public void setInputFile(DelayedFile file)
    {
        this.inputFile = file;
    }

    public void setOutputFile(DelayedFile file)
    {
        this.outputFile = file;
    }

    public File getInputFile()
    {
        return inputFile.call();
    }

    public File getOutputFile()
    {
        return outputFile == null ? getInputFile() : outputFile.call();
    }
}

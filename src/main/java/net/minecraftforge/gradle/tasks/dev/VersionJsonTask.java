package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class VersionJsonTask extends DefaultTask
{
    private static final Gson GSON_FORMATTER = new GsonBuilder().setPrettyPrinting().create();
    
    @InputFile
    DelayedFile input;
    
    @OutputFile
    DelayedFile output;

    @SuppressWarnings("unchecked")
    @TaskAction
    public void doTask() throws IOException
    {

        String data = Files.toString(getInput(), Charsets.UTF_8);
        Map<String, Object> json = (Map<String, Object>)new Gson().fromJson(data, Map.class);
        json = (Map<String, Object>)json.get("versionInfo");
        data = GSON_FORMATTER.toJson(json);
        Files.write(data.getBytes(), getOutput());
    }

    public File getInput()
    {
        return input.call();
    }

    public void setInput(DelayedFile input)
    {
        this.input = input;
    }

    public File getOutput()
    {
        return output.call();
    }

    public void setOutput(DelayedFile output)
    {
        this.output = output;
    }
    
}

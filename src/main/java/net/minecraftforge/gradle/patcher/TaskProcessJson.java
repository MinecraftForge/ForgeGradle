package net.minecraftforge.gradle.patcher;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

class TaskProcessJson extends DefaultTask
{
    private Map<String, Object> replacements = Maps.newHashMap();

    @InputFile
    private Object              releaseJson;

    @OutputFile
    private Object              installerJson;

    @OutputFile
    private Object              universalJson;

    //@formatter:off
    public TaskProcessJson() {}
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException
    {
        File inputFile = getReleaseJson();
        File outFile = getInstallerJson();
        File truncatedFile = getUniversalJson();

        String input = Files.toString(inputFile, Constants.CHARSET);

        for (Entry<String, Object> e : replacements.entrySet())
        {
            input = input.replace(e.getKey(), Constants.resolveString(e.getValue()));
        }

        // write the replaced output
        Files.write(input.getBytes(Constants.CHARSET), outFile);

        // get just the useful data
        Gson gson = (new GsonBuilder()).setPrettyPrinting().create();
        input = gson.toJson(gson.fromJson(input, Map.class).get("versionInfo"));

        // write the useful truncated data
        Files.write(input.getBytes(Constants.CHARSET), truncatedFile);
    }

    public Map<String, Object> getReplacements()
    {
        return replacements;
    }

    public void addReplacement(Object key, Object value)
    {
        replacements.put(Constants.resolveString(key), value);
    }

    public void addReplacements(Map<String, Object> things)
    {
        replacements.putAll(things);
    }

    public File getReleaseJson()
    {
        return getProject().file(releaseJson);
    }

    public void setReleaseJson(Object releaseJson)
    {
        this.releaseJson = releaseJson;
    }

    protected boolean isReleaseJsonNull()
    {
        return releaseJson == null;
    }

    protected File getInstallerJson()
    {
        return getProject().file(installerJson);
    }

    protected void setInstallerJson(Object installerJson)
    {
        this.installerJson = installerJson;
    }

    protected File getUniversalJson()
    {
        return getProject().file(universalJson);
    }

    protected void setUniversalJson(Object universalJson)
    {
        this.universalJson = universalJson;
    }
}

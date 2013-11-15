package net.minecraftforge.gradle.tasks;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask extends CachedTask
{
    @Input
    private DelayedString url;

    @OutputFile
    @Cached
    private DelayedFile output;

    @TaskAction
    public void doTask() throws IOException
    {
        File outputFile = getProject().file(getOutput());
        outputFile.getParentFile().mkdirs();
        outputFile.createNewFile();

        getLogger().info("Downloading " + getUrl() + " to " + outputFile);

        HttpURLConnection connect = (HttpURLConnection) (new URL(getUrl())).openConnection();
        connect.setInstanceFollowRedirects(true);

        InputStream inStream = connect.getInputStream();
        OutputStream outStream = new FileOutputStream(outputFile);

        int data = inStream.read();
        while (data != -1)
        {
            outStream.write(data);

            // read next
            data = inStream.read();
        }

        inStream.close();
        outStream.flush();
        outStream.close();

        getLogger().info("Download complete");
    }

    public File getOutput()
    {
        return output.call();
    }

    public void setOutput(DelayedFile output)
    {
        this.output = output;
    }

    public String getUrl()
    {
        return url.call();
    }

    public void setUrl(DelayedString url)
    {
        this.url = url;
    }
}

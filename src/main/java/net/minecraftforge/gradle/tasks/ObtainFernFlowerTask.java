package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ObtainFernFlowerTask extends CachedTask
{
    @Input
    private DelayedString mcpUrl;

    @Cached
    @OutputFile
    private DelayedFile ffJar;

    @TaskAction
    public void doTask() throws MalformedURLException, IOException
    {
        if (getProject().getGradle().getStartParameter().isOffline())
        {
            getLogger().error("Offline mode! not downloading Fernflower!");
            this.setDidWork(false);
            return;
        }
        
        File ff = getFfJar();
        String url = getMcpUrl();

        getLogger().debug("Downloading " + url);
        getLogger().debug("Fernflower output location " + ff);

        HttpURLConnection connect = (HttpURLConnection) (new URL(url)).openConnection();
        connect.setRequestProperty("User-Agent", Constants.USER_AGENT);
        connect.setInstanceFollowRedirects(true);

        final ZipInputStream zin = new ZipInputStream(connect.getInputStream());
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {
            if (StringUtils.lower(entry.getName()).endsWith("fernflower.jar"))
            {
                ff.getParentFile().mkdirs();
                Files.touch(ff);
                Files.write(ByteStreams.toByteArray(zin), ff);
            }
        }

        zin.close();

        getLogger().info("Download and Extraction complete");
    }

    public String getMcpUrl()
    {
        return mcpUrl.call();
    }
    
    public void setMcpUrl(DelayedString mcpUrl)
    {
        this.mcpUrl = mcpUrl;
    }
    
    public File getFfJar()
    {
        return ffJar.call();
    }
    
    public void setFfJar(DelayedFile ffJar)
    {
        this.ffJar = ffJar;
    }
}

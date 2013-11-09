package net.minecraftforge.gradle.tasks;

import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ObtainMcpStuffTask extends CachedTask
{
    @Input
    private DelayedString mcpUrl;

    @OutputFile
    private DelayedFile ffJar;

    @OutputFile
    private DelayedFile injectorJar;

    @TaskAction
    public void doTask() throws MalformedURLException, IOException
    {
        File ff = getFfJar();
        File exc = getInjectorJar();
        String url = getMcpUrl();

        getLogger().info("Downloading " + url);
        getLogger().info("Fernflower output location " + ff);
        getLogger().info("Injector output location " + exc);

        HttpURLConnection connect = (HttpURLConnection) (new URL(url)).openConnection();
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
            else if (StringUtils.lower(entry.getName()).endsWith("mcinjector.jar"))
            {
                exc.getParentFile().mkdirs();
                Files.touch(exc);
                Files.write(ByteStreams.toByteArray(zin), exc);
            }
            else if (StringUtils.lower(entry.getName()).endsWith("version.cfg"))
            {
                String mcpVersionData = StringUtils.fromUTF8Stream(zin);
                Splitter splitter = Splitter.on('=').trimResults();

                for (String line : StringUtils.lines(mcpVersionData))
                {
                    String[] lineParts = Iterables.toArray(splitter.split(line),String.class);
                    if (lineParts.length > 1 && "MCPVersion".equals(lineParts[0]))
                    {
                        BaseExtension exten = (BaseExtension)getProject().getExtensions().getByName(EXT_NAME_MC);
                        exten.setMcpVersion(lineParts[1]);
                        getLogger().info("MCP data version " + lineParts[1]);
                    }
                }
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
    public File getInjectorJar()
    {
        return injectorJar.call();
    }
    public void setInjectorJar(DelayedFile injectorJar)
    {
        this.injectorJar = injectorJar;
    }
}

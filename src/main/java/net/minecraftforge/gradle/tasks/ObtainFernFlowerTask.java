/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.gradle.util.delayed.DelayedString;

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
            if (Constants.lower(entry.getName()).endsWith("fernflower.jar"))
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

/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.StandardOutputListener;

import com.google.common.io.Files;

public class FileLogListenner implements StandardOutputListener, BuildListener
{
    private final File out;
    private BufferedWriter writer;
    
    public FileLogListenner(File file)
    {
        out = file;
        
        try
        {
            if (out.exists())
                out.delete();
            else
                out.getParentFile().mkdirs();
            
            out.createNewFile();
            
            writer = Files.newWriter(out, Charset.defaultCharset());
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void projectsLoaded(Gradle arg0) {}
    
    @Override
    public void buildStarted(Gradle arg0) {}

    @Override
    public void onOutput(CharSequence arg0)
    {
        try
        {
            writer.write(arg0.toString());
        }
        catch (IOException e)
        {
            // to stop recursion....
        }
    }

    @Override
    public void buildFinished(BuildResult arg0)
    {
        try
        {
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void projectsEvaluated(Gradle arg0) {}  // nothing
    
    @Override
    public void settingsEvaluated(Settings arg0) {} // nothing

}

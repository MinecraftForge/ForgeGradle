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
package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

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

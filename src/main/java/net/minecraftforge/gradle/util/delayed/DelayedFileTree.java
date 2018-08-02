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
package net.minecraftforge.gradle.util.delayed;

import java.io.File;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;

@SuppressWarnings("serial")
public class DelayedFileTree extends DelayedBase<FileTree>
{
    protected final File hardcoded;
    protected transient final Project project;
    
    public DelayedFileTree(Class<?> owner, File file)
    {
        super(owner, (TokenReplacer)null);
        hardcoded = file;
        project = null;
    }
    
    public DelayedFileTree(Class<?> owner, Project project, ReplacementProvider provider, String pattern)
    {
        super(owner, provider, pattern);
        hardcoded = null;
        this.project = project;
    }
    
    public DelayedFileTree(Class<?> owner, Project project, TokenReplacer replacer)
    {
        super(owner, replacer);
        hardcoded = null;
        this.project = project;
        
    }

    @Override
    public FileTree resolveDelayed(String replaced)
    {
        String name;
        File file;
        
        if (hardcoded != null)
        {
            name = hardcoded.getName();
            file = hardcoded;
        }
        else
        {
            name = replaced;
            file = project.file(replaced);
        }
        
        if (name.endsWith(".jar") || name.endsWith(".zip"))
        {
            return project.zipTree(file);
        }
        else
        {
            return project.fileTree(file);
        }
    }
}

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

@SuppressWarnings("serial")
public class DelayedFile extends DelayedBase<File>
{
    protected final File hardcoded;
    protected transient final Project project;
    
    public DelayedFile(Class<?> owner, File file)
    {
        super(owner, (TokenReplacer)null);
        hardcoded = file;
        project = null;
    }
    
    public DelayedFile(Class<?> owner, Project project, ReplacementProvider provider, String pattern)
    {
        super(owner, provider, pattern);
        hardcoded = null;
        this.project = project;
    }
    
    public DelayedFile(Class<?> owner, Project project, TokenReplacer replacer)
    {
        super(owner, replacer);
        hardcoded = null;
        this.project = project;
        
    }

    @Override
    public File resolveDelayed(String replaced)
    {
        if (hardcoded != null)
            return hardcoded;
        
        return project.file(replaced);
    }

    public DelayedFileTree toZipTree()
    {
        if (hardcoded != null)
            return new DelayedFileTree(DelayedFile.class, hardcoded);
        else
            return new DelayedFileTree(DelayedFile.class, project, replacer);
        
    }
}

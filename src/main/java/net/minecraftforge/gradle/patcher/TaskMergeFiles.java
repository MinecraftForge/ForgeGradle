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
package net.minecraftforge.gradle.patcher;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

class TaskMergeFiles extends DefaultTask
{
    @InputFiles
    private final List<Object> inSrgs = Lists.newArrayListWithExpectedSize(3);

    @OutputFile
    private Object             outSrg;

    @InputFiles
    private final List<Object> inExcs = Lists.newArrayListWithExpectedSize(3);

    @OutputFile
    private Object             outExc;

    @InputFiles
    private final List<Object> inAts  = Lists.newArrayListWithExpectedSize(3);

    @OutputFile
    private Object             outAt;

    //@formatter:off
    public TaskMergeFiles() {}
    //@formatter:on

    @TaskAction
    public void mergeFiles() throws IOException
    {
        mergeFiles(getInSrgs(), ".srg", getOutSrg());
        mergeFiles(getInExcs(), ".exc", getOutExc());
        mergeFiles(getInAts(), "_at.cfg", getOutAt());
    }

    private void mergeFiles(FileCollection in, String ending, File out) throws IOException
    {
        Set<String> lines = Sets.newLinkedHashSet();
        for (File f : in.getFiles())
        {
            if (f.isDirectory() || !f.exists() || !f.getName().endsWith(ending))
                continue;
            lines.addAll(Files.readLines(f, Constants.CHARSET));
        }

        out.getParentFile().mkdirs();
        Files.write(Joiner.on('\n').join(lines), out, Constants.CHARSET);
    }

    public File getOutSrg()
    {
        return getProject().file(outSrg);
    }

    public void setOutSrg(Object outSrg)
    {
        this.outSrg = outSrg;
    }

    public File getOutExc()
    {
        return getProject().file(outExc);
    }

    public void setOutExc(Object outExc)
    {
        this.outExc = outExc;
    }

    public File getOutAt()
    {
        return getProject().file(outAt);
    }

    public void setOutAt(Object outAt)
    {
        this.outAt = outAt;
    }

    public FileCollection getInSrgs()
    {
        return getProject().files(inSrgs);
    }

    public void addSrg(Object srg)
    {
        inSrgs.add(srg);
    }

    public FileCollection getInExcs()
    {
        return getProject().files(inExcs);
    }

    public void addExc(Object exc)
    {
        inExcs.add(exc);
    }

    public FileCollection getInAts()
    {
        return getProject().files(inAts);
    }

    public void addAt(Object at)
    {
        inAts.add(at);
    }
}

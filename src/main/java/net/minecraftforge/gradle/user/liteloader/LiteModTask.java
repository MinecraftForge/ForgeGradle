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
package net.minecraftforge.gradle.user.liteloader;

import java.io.File;
import java.io.IOException;

import org.apache.tools.ant.taskdefs.BuildNumber;
import org.gradle.api.AntBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import groovy.lang.Closure;

public class LiteModTask extends DefaultTask
{
    private String buildNumber;
    
    private Object fileName;
    
    private LiteModJson json;
    
    @OutputFile
    private File output;
    
    public LiteModTask()
    {
        this.setFileName("litemod.json");
        this.getOutputs().upToDateWhen(new Spec<Task>()
        {
            @Override
            public boolean isSatisfiedBy(Task arg0)
            {
                return false;
            }
        });
    }

    @TaskAction
    public void doTask() throws IOException
    {
        LiteModJson json = LiteModTask.this.getJson();
        if (json.revision == null)
        {
            json.setRevision(getBuildNumber());
        }
        File outputFile = this.getOutput();
        outputFile.delete();
        json.toJsonFile(outputFile);
    }
    
    public Object getFileName()
    {
        return this.fileName;
    }
    
    public void setFileName(Object fileName)
    {
        this.fileName = fileName;
    }

    public File getOutput()
    {
        if (this.output == null)
        {
            this.output = getProject().file(new File(this.getTemporaryDir(), this.getFileName().toString()));
        }
        return this.output;
    }
    
    public LiteModJson getJson() throws IOException
    {
        if (this.json == null)
        {
            Project project = this.getProject();
            String version = project.getExtensions().findByType(LiteloaderExtension.class).getVersion();
            this.json = new LiteModJson(project, version);
        }

        return this.json;
    }
    
    public void json(Closure<?> configureClosure) throws IOException
    {
        ClosureBackedAction.execute(this.getJson(), configureClosure);
    }

    public String getBuildNumber() throws IOException
    {
        if (this.buildNumber == null)
        {
            AntBuilder ant = getProject().getAnt();

            File buildNumberFile = new File("build.number");
            BuildNumber buildNumber = (BuildNumber)ant.invokeMethod("buildnumber");
            buildNumber.setFile(buildNumberFile);
            buildNumber.execute();
            
            this.buildNumber = ant.getAntProject().getProperty("build.number");
        }
        
        return this.buildNumber;
    }
}

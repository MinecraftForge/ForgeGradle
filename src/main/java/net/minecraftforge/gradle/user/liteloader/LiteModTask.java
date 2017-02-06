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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.GsonBuilder;
import net.minecraftforge.gradle.tasks.AbstractJsonTask;
import org.apache.tools.ant.taskdefs.BuildNumber;
import org.gradle.api.AntBuilder;
import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public class LiteModTask extends AbstractJsonTask<LiteModJson>
{
    private String buildNumber;

    public LiteModTask()
    {
        this.setFileName("litemod.json");
    }

    @Override
    public void doTask() throws IOException {
        LiteModJson json = getJson();
        if (json.revision == null) {
            json.setRevision(getBuildNumber());
        }
        super.doTask();
    }

    @Override
    protected LiteModJson createJson() {
        Project project = this.getProject();
        String version = project.getExtensions().findByType(LiteloaderExtension.class).getVersion();
        return new LiteModJson(project, version);
    }

    @Override
    protected GsonBuilder withGsonBuilder(GsonBuilder gson) {
        return gson.registerTypeAdapter(LiteModDescription.class, new LiteModDescription.JsonAdapter());
    }

    private String getBuildNumber() throws IOException {
        if (this.buildNumber == null)
        {
            AntBuilder ant = getProject().getAnt();

            File buildNumberFile = new File(this.getTemporaryDir(), "build.number");
            BuildNumber buildNumber = (BuildNumber)ant.invokeMethod("buildnumber");
            buildNumber.setFile(buildNumberFile);
            buildNumber.execute();

            Properties props = new Properties();
            props.load(Files.newReader(buildNumberFile, Charsets.ISO_8859_1));
            this.buildNumber = props.getProperty("build.number");
        }

        return this.buildNumber;
    }
}

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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class LiteModJson
{
    public String name, displayName, version, author;
    public String mcversion, revision;
    public String injectAt, tweakClass;
    public List<String> classTransformerClasses;
    public List<String> dependsOn;
    public List<String> requiredAPIs;
    public List<String> mixinConfigs;
    public String description;
    private transient Map<String, String> descriptionMap;

    private transient final Project project;
    private transient final String minecraftVersion;

    LiteModJson(Project project, String minecraftVersion)
    {
        this.project = project;
        this.mcversion = this.minecraftVersion = minecraftVersion;

        this.name = project.getName();
        this.displayName = project.hasProperty("displayName") ? project.property("displayName").toString() : project.getDescription();
        this.version = project.getVersion().toString();
    }

    public void setMcversion(String version)
    {
        this.mcversion = version;
    }

    public void setRevision(String revision)
    {
        this.revision = revision;
    }

    public void setDescription(String desc)
    {
        this.description = desc;
    }

    public Map<String, String> getDescription()
    {
        if (this.descriptionMap == null)
            this.descriptionMap = Maps.newHashMap();
        return this.descriptionMap;
    }

    public List<String> getClassTransformerClasses()
    {
        if (this.classTransformerClasses == null)
        {
            this.classTransformerClasses = new ArrayList<String>();
        }
        return this.classTransformerClasses;
    }

    public List<String> getDependsOn()
    {
        if (this.dependsOn == null)
        {
            this.dependsOn = new ArrayList<String>();
        }
        return this.dependsOn;
    }

    public List<String> getRequiredAPIs()
    {
        if (this.requiredAPIs == null)
        {
            this.requiredAPIs = new ArrayList<String>();
        }
        return this.requiredAPIs;
    }

    public List<String> getMixinConfigs()
    {
        if (this.mixinConfigs == null)
        {
            this.mixinConfigs = new ArrayList<String>();
        }
        return this.mixinConfigs;
    }

    public void toJsonFile(File outputFile) throws IOException
    {
        this.validate();

        Gson gson = new Gson();
        JsonObject json = gson.fromJson(gson.toJson(this), JsonObject.class);
        if (this.descriptionMap != null)
        {
            for (Entry<String, String> desc : this.descriptionMap.entrySet())
            {
                json.addProperty("description." + desc.getKey(), desc.getValue());
            }
        }
        
        FileWriter writer = new FileWriter(outputFile);
        new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        writer.flush();
        writer.close();
    }

    private void validate()
    {
        if (Strings.isNullOrEmpty(this.name))
            throw new InvalidUserDataException("litemod json is missing property [name]");

        if (Strings.isNullOrEmpty(this.version))
            throw new InvalidUserDataException("litemod json is missing property [version]");

        if (Strings.isNullOrEmpty(this.mcversion))
            throw new InvalidUserDataException("litemod json is missing property [mcversion]");

        if (Strings.isNullOrEmpty(this.revision))
            throw new InvalidUserDataException("litemod json is missing property [revision]");

        try
        {
            Float.parseFloat(this.revision);
        }
        catch (NumberFormatException ex)
        {
            throw new InvalidUserDataException("invalid format for [revision] property in litemod.json, expected float");
        }

        if (!this.minecraftVersion.equals(this.mcversion))
        {
            this.project.getLogger().warn("You are setting a different target version of minecraft to the build environment");
        }

    }
}

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
package net.minecraftforge.gradle.user.liteloader;

import com.google.common.base.Strings;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class LiteModJson
{
    public static class Description extends HashMap<String, Object>
    {
        public static final String BASE = "";

        private static final long serialVersionUID = 1L;

        @Override
        public String toString()
        {
            Object value = this.get(Description.BASE);
            return value == null ? Description.BASE : value.toString();
        }

        public void propertyMissing(String name, Object value)
        {
            this.put(name, value);
        }

        public Object propertyMissing(String name)
        {
            return this.get(name);
        }

        static class JsonAdapter extends TypeAdapter<Description>
        {
            @Override
            public void write(JsonWriter out, Description value) throws IOException
            {
                if (value == null)
                {
                    out.nullValue();
                    return;
                }

                out.value(value.toString());
                for (Entry<String, Object> entry : value.entrySet())
                {
                    if (!entry.getKey().equals(Description.BASE) && entry.getValue() != null)
                    {
                        out.name("description." + entry.getKey()).value(entry.getValue().toString());
                    }
                }
            }

            @Override
            public Description read(JsonReader in) throws IOException
            {
                return null;
            }
        }
    }

    public String name, displayName, version, author;
    public String mcversion, revision;
    public String injectAt, tweakClass;
    public List<String> classTransformerClasses;
    public List<String> dependsOn;
    public List<String> requiredAPIs;
    public List<String> mixinConfigs;

    /**
     * Handle base description and sub-descriptions dynamically
     */
    public Description description;

    private transient final Project project;
    private transient final String minecraftVersion;

    LiteModJson(Project project, String minecraftVersion, String revision)
    {
        this.project = project;
        this.mcversion = this.minecraftVersion = minecraftVersion;
        this.revision = revision;

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

    public Description getDescription()
    {
        if (this.description == null)
        {
            this.description = new Description();
        }
        return this.description;
    }

    public void setDescription(Object value)
    {
        this.getDescription().put(Description.BASE, value);
    }

    public void toJsonFile(File outputFile) throws IOException
    {
        this.validate();

        FileWriter writer = new FileWriter(outputFile);
        new GsonBuilder().registerTypeAdapter(Description.class, new Description.JsonAdapter()).setPrettyPrinting().create().toJson(this, writer);
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

        if (!this.minecraftVersion.equals(this.mcversion)) {
            this.project.getLogger().warn("You are setting a different target version of minecraft to the build environment");
        }

    }

}

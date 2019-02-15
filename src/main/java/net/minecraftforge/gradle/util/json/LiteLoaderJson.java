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
package net.minecraftforge.gradle.util.json;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

public class LiteLoaderJson
{
    public MetaObject meta;
    public Map<String, VersionObject> versions;
    
    public static final class MetaObject
    {
        public String description, authors, url;
    }
    
    public static final class RepoObject
    {
        public String stream, type, url, classifier;
        
        @Override
        public String toString()
        {
            return String.format("Repository: %s:%s", type, url);
        }
        
        String getClassifier()
        {
            return classifier == null || classifier.isEmpty() ? "" : ":" + classifier;
        }
    }
    
    public static final class SnapshotsObject
    {
        public List<Map<String, String>> libraries;
    }

    public static final class VersionObject
    {
        public Artifact latest;
        public List<Artifact> artifacts;
        public RepoObject repo;
        public SnapshotsObject snapshots;
    }
    
    public static final class Artifact
    {
        public static final String SNAPSHOT_STREAM = "SNAPSHOT";
        public static final String DEFAULT_TWEAKER = "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
        public static final String DEFAULT_ARTEFACT = "com.mumfrey:liteloader";
        
        public String group, md5, tweakClass, file, version, mcpJar, srcJar;
        public long timestamp;
        public List<Map<String, String>> libraries;

        public Artifact() {}
        
        Artifact(String version, RepoObject repo, SnapshotsObject snapshots)
        {
            String suffix = Artifact.SNAPSHOT_STREAM.equals(repo.stream) ? "-" + Artifact.SNAPSHOT_STREAM : "";

            this.group = Artifact.DEFAULT_ARTEFACT;
            this.tweakClass = Artifact.DEFAULT_TWEAKER;
            this.version = version + suffix;
            this.libraries = snapshots != null ? snapshots.libraries : null;
        }
        
        public List<Map<String, String>> getLibraries()
        {
            return this.libraries != null ? this.libraries : Collections.<Map<String, String>>emptyList();
        }
        
        public boolean hasMcp()
        {
            return mcpJar != null;
        }
        
        public String getDepString(RepoObject repo)
        {
            return group + ":" + version + repo.getClassifier();
        }
    }
    
    public static final class VersionAdapter implements JsonDeserializer<VersionObject>
    {
        @Override
        public VersionObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
        {
            VersionObject obj = new VersionObject();
            obj.artifacts = new LinkedList<Artifact>();
            
            JsonObject repoData = json.getAsJsonObject().getAsJsonObject("repo");
            if (repoData != null)
            {
                obj.repo = context.deserialize(repoData, RepoObject.class);
            }

            JsonObject snapshotsData = json.getAsJsonObject().getAsJsonObject("snapshots");
            if (snapshotsData != null)
            {
                obj.snapshots = context.deserialize(snapshotsData, SnapshotsObject.class);
            }
            
            JsonObject groupLevel = json.getAsJsonObject().getAsJsonObject("artefacts");
            if (groupLevel != null)
            {
                // itterate over the groups
                for (Entry<String, JsonElement> groupE : groupLevel.entrySet())
                {
                    String group = groupE.getKey();
                    
                    // itterate over the artefacts in the groups
                    for (Entry<String, JsonElement> artifactE : groupE.getValue().getAsJsonObject().entrySet())
                    {
                        Artifact artifact = context.deserialize(artifactE.getValue(), Artifact.class);
                        artifact.group = group;
                        
                        if ("latest".equals(artifactE.getKey()))
                        {
                            obj.latest = artifact;
                        }
                        else
                        {
                            obj.artifacts.add(artifact);
                        }
                        
                    }
                }
            }

            return obj;
        }
    }
    
    LiteLoaderJson addDefaultArtifacts()
    {
        for (Entry<String, VersionObject> versionEntry : this.versions.entrySet())
        {
            String version = versionEntry.getKey();
            VersionObject data = versionEntry.getValue();
            if (data.artifacts.size() == 0 && "SNAPSHOT".equals(data.repo.stream))
            {
                // Add snapshot artifact
                data.latest = new Artifact(version, data.repo, data.snapshots);
                data.artifacts.add(data.latest);
            }
        }
        
        return this;
    }

}

package net.minecraftforge.gradle.json;

import java.lang.reflect.Type;
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
    
    public static final class VersionObject
    {
        public Artifact latest;
        public List<Artifact> artifacts;
    }
    
    public static final class Artifact
    {
        public String group, md5, tweakClass, file, version, mcpJar, srcJar;
        public long timestamp;
        
        public boolean hasMcp()
        {
            return mcpJar != null;
        }
        
        public String getMcpDepString()
        {
            return group + ":" + version + "-mcpnames"; 
        }
    }
    
    public static final class VersionAdapter implements JsonDeserializer<VersionObject>
    {
        @Override
        public VersionObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
        {
            VersionObject obj = new VersionObject();
            obj.artifacts = new LinkedList<Artifact>();
            
            JsonObject groupLevel = json.getAsJsonObject().getAsJsonObject("artefacts");
            
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
                    
            
            return obj;
        }
    }
}

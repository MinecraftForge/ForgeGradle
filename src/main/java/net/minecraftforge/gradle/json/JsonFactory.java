package net.minecraftforge.gradle.json;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.json.LiteLoaderJson.VersionObject;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.Version;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class JsonFactory
{
    public static final Gson GSON;
    static
    {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapterFactory(new EnumAdaptorFactory());
        builder.registerTypeAdapter(Date.class, new DateAdapter());
        builder.registerTypeAdapter(File.class, new FileAdapter());
        builder.registerTypeAdapter(VersionObject.class, new LiteLoaderJson.VersionAdapter());
        builder.enableComplexMapKeySerialization();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    public static Version loadVersion(File json) throws JsonSyntaxException, JsonIOException, IOException
    {
        FileReader reader = new FileReader(json);
        Version v =  GSON.fromJson(reader, Version.class);
        reader.close();
        return v;
    }
    
    public static AssetIndex loadAssetsIndex(File json) throws JsonSyntaxException, JsonIOException, IOException
    {
        FileReader reader = new FileReader(json);
        AssetIndex a =  GSON.fromJson(reader, AssetIndex.class);
        reader.close();
        return a;
    }
    
    public static LiteLoaderJson loadLiteLoaderJson(File json) throws JsonSyntaxException, JsonIOException, IOException
    {
        FileReader reader = new FileReader(json);
        LiteLoaderJson a =  GSON.fromJson(reader, LiteLoaderJson.class);
        reader.close();
        return a;
    }

    public static Map<String, MCInjectorStruct> loadMCIJson(File json) throws IOException
    {
        FileReader reader = new FileReader(json);
        Map<String, MCInjectorStruct> ret = new LinkedHashMap<String, MCInjectorStruct>();

        JsonObject object = (JsonObject)new JsonParser().parse(reader);
        reader.close();

        for (Entry<String, JsonElement> entry : object.entrySet())
        {
            ret.put(entry.getKey(), GSON.fromJson(entry.getValue(), MCInjectorStruct.class));
        }
        return ret;
    }
}

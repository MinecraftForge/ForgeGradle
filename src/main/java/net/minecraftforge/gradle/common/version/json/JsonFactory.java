package net.minecraftforge.gradle.common.version.json;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Date;

import net.minecraftforge.gradle.common.version.Version;

import com.google.gson.*;

public class JsonFactory
{
    public static final Gson GSON;
    static
    {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapterFactory(new EnumAdaptorFactory());
        builder.registerTypeAdapter(Date.class, new DateAdapter());
        builder.registerTypeAdapter(File.class, new FileAdapter());
        builder.enableComplexMapKeySerialization();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    public static Version loadVersion(File json) throws JsonSyntaxException, JsonIOException, FileNotFoundException
    {
        return GSON.fromJson(new FileReader(json), Version.class);
    }
}

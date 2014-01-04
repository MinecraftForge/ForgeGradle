package net.minecraftforge.gradle.json;

import java.io.File;
import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class FileAdapter extends TypeAdapter<File>
{

    @Override
    public File read(JsonReader json) throws IOException
    {
        if (json.hasNext())
        {
            String value = json.nextString();
            return value == null ? null : new File(value);
        }
        return null;
    }

    @Override
    public void write(JsonWriter json, File value) throws IOException
    {
        if (value == null)
        {
            json.nullValue();
        }
        else
        {
            json.value(value.getCanonicalPath());
        }
    }
}

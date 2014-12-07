package net.minecraftforge.gradle.json.forgeversion;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ForgeArtifactAdapter extends TypeAdapter<ForgeArtifact>
{

    @Override
    public void write(JsonWriter out, ForgeArtifact value) throws IOException
    {
        // dont really need to do this.. but wtvr...
        out.beginArray();
        out.value(value.ext);
        out.value(value.classifier);
        out.value(value.hash);
        out.endArray();
    }

    @Override
    public ForgeArtifact read(JsonReader in) throws IOException
    {
        ForgeArtifact out = new ForgeArtifact();
        
        in.beginArray();

        out.ext = in.nextString();
        out.classifier = in.nextString();
        out.hash = in.nextString();
        
        in.endArray();
        
        return out;
    }

}

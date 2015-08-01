package net.minecraftforge.gradle.util.json.fgversion;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public class FGVersionDeserializer implements JsonDeserializer<FGVersionWrapper>
{
    @Override
    public FGVersionWrapper deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        FGVersionWrapper wrapper = new FGVersionWrapper();

        List<FGVersion> versions = context.deserialize(json, new TypeToken<List<FGVersion>>() {}.getType());

        for (int i = 0; i < versions.size(); i++)
        {
            FGVersion v = versions.get(i);
            v.index = i;
            wrapper.versions.add(v.version);
            wrapper.versionObjects.put(v.version, v);
        }

        return wrapper;
    }
}

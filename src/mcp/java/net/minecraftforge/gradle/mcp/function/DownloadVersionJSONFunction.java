package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.internal.impldep.com.google.gson.JsonElement;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public class DownloadVersionJSONFunction extends AbstractFileDownloadFunction {

    private static final String DEFAULT_OUTPUT = "version.json";

    public DownloadVersionJSONFunction() {
        super(env -> DEFAULT_OUTPUT, DownloadVersionJSONFunction::getDownloadInfo);
    }

    private static DownloadInfo getDownloadInfo(MCPEnvironment environment) {
        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput(DownloadManifestFunction.class));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            // Look for the version we want and return its URL
            for (JsonElement e : json.getAsJsonArray("versions")) {
                String v = e.getAsJsonObject().get("id").getAsString();
                if (v.equals(environment.mcVersion)) {
                    return new DownloadInfo(e.getAsJsonObject().get("url").getAsString(), null);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }

}

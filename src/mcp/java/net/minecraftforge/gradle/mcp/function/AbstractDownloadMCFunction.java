package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.gradle.internal.hash.HashValue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

public abstract class AbstractDownloadMCFunction extends AbstractFileDownloadFunction {

    public AbstractDownloadMCFunction(String artifact) {
        super(env -> artifact + ".jar", env -> getDownloadInfo(env, artifact));
    }

    private static DownloadInfo getDownloadInfo(MCPEnvironment environment, String artifact) {
        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput(DownloadVersionJSONFunction.class));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            JsonObject artifactInfo = json.getAsJsonObject("downloads").getAsJsonObject(artifact);
            String url = artifactInfo.get("url").getAsString();
            HashValue hash = HashValue.parse(artifactInfo.get("sha1").getAsString());
            return new DownloadInfo(url, hash);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}

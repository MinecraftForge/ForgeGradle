package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;
import net.minecraftforge.gradle.forgedev.mcp.util.MavenArtifactDownloader;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.internal.impldep.com.google.gson.JsonElement;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

public class ListLibrariesFunction implements MCPFunction {

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        String outputString = environment.getArguments().getOrDefault("output", "libraries.txt");
        File output = environment.getFile(outputString);

        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput(DownloadVersionJSONFunction.class));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            // Gather all the libraries
            Set<File> files = new HashSet<>();
            for (JsonElement libElement : json.getAsJsonArray("libraries")) {
                JsonObject library = libElement.getAsJsonObject();
                Set<File> libFiles = MavenArtifactDownloader.download(environment.project, library.get("name").getAsString());
                files.addAll(libFiles);
            }

            // Write the list
            if (output.exists()) output.delete();
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter writer = new PrintWriter(output);
            for (File file : files) {
                writer.println("-e=" + file.getAbsolutePath());
            }
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return output;
    }

}

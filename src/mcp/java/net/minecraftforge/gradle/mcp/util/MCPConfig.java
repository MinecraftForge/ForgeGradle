package net.minecraftforge.gradle.mcp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.function.ExecuteFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MCPConfig {

    private static int MAGIC_NUMBER = 0;

    private final String mcVersion;
    private final File configZip;
    private final List<Step> sharedSteps, srcSteps;
    private final Set<String> libraries;

    public MCPConfig(String mcVersion, File configZip, List<Step> sharedSteps, List<Step> srcSteps, Set<String> libraries) {
        this.mcVersion = mcVersion;
        this.configZip = configZip;
        this.sharedSteps = sharedSteps;
        this.srcSteps = srcSteps;
        this.libraries = libraries;
    }

    public String getMCVersion() {
        return mcVersion;
    }

    public File getConfigZip() {
        return configZip;
    }

    public List<Step> getSharedSteps() {
        return sharedSteps;
    }

    public List<Step> getSrcSteps() {
        return srcSteps;
    }

    public Set<String> getLibraries() {
        return libraries;
    }

    public static class Step {

        private final String name;
        private final MCPFunction function;
        private final Map<String, String> arguments;

        private Step(String name, MCPFunction function, Map<String, String> arguments) {
            this.name = name;
            this.function = function;
            this.arguments = arguments;
        }

        public String getName() {
            return name;
        }

        public MCPFunction getFunction() {
            return function;
        }

        public Map<String, String> getArguments() {
            return arguments;
        }

    }

    public static MCPConfig deserialize(Project project, File zip, JsonObject json, String pipeline) {
        int spec = json.get("spec").getAsInt();
        switch (spec) {
            case 1:
                return deserializeV1(project, zip, pipeline, json);
            default:
                throw new JsonParseException("Unsupported specification: " + spec);
        }
    }

    private static MCPConfig deserializeV1(Project project, File zip, String pipeline, JsonObject json) {
        // Read JSON
        String mcVersion = json.get("version").getAsString();
        JsonObject data = json.get("data").getAsJsonObject();
        JsonArray stepArray = json.get("steps").getAsJsonObject().get(pipeline).getAsJsonArray();
        JsonObject functions = json.get("functions").getAsJsonObject();
        JsonArray libraryArray = json.get("libraries").getAsJsonObject().get(pipeline).getAsJsonArray();

        // Compute steps
        List<Step> sharedSteps = new LinkedList<>();
        List<Step> srcSteps = new LinkedList<>();
        boolean decompiled = false;

        for (JsonElement stepElement : stepArray) {
            JsonObject stepObject = stepElement.getAsJsonObject();
            String type = stepObject.get("type").getAsString();
            String name = stepObject.has("name") ? stepObject.get("name").getAsString() : type;

            MCPFunction function = MCPPlugin.createBuiltInFunction(type);
            if (function == null) {
                if (!functions.has(type)) throw new JsonParseException("Unsupported function type: " + type);
                function = deserializeV1Function(project, functions.get(type).getAsJsonObject());
            }

            try {
                function.loadData(data);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            Map<String, String> arguments = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : stepObject.entrySet()) {
                if (entry.getKey().equals("name") || entry.getKey().equals("type")) continue;
                arguments.put(entry.getKey(), entry.getValue().getAsString());
            }

            if (decompiled |= name.equals("decompile")) {
                srcSteps.add(new Step(name, function, arguments));
            } else {
                sharedSteps.add(new Step(name, function, arguments));
            }
        }

        // Compute library list
        Set<String> libraries = StreamSupport.stream(libraryArray.spliterator(), false)
                .map(JsonElement::getAsString).collect(Collectors.toSet());

        // Create config
        return new MCPConfig(mcVersion, zip, sharedSteps, srcSteps, libraries);
    }

    private static MCPFunction deserializeV1Function(Project project, JsonObject json) {
        String version = json.get("version").getAsString();
        String repo = json.get("repo").getAsString();
        JsonArray runArgArray = json.get("args").getAsJsonArray();
        JsonArray jvmArgArray = json.get("jvmargs").getAsJsonArray();

        // Download the function's artifact
        project.getRepositories().maven(r -> {
            r.setName(repo + " - " + MAGIC_NUMBER++);
            r.setUrl(repo);
        });
        Set<File> artifacts = MavenArtifactDownloader.download(project, version);
        File artifact = artifacts.iterator().next();

        // Compute argument arrays
        String[] runArgs = Utils.toArray(runArgArray, JsonElement::getAsString, String[]::new);
        String[] jvmArgs = Utils.toArray(jvmArgArray, JsonElement::getAsString, String[]::new);

        return new ExecuteFunction(artifact, jvmArgs, runArgs, Collections.emptyMap());
    }

}

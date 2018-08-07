package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.RawMCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.com.google.gson.Gson;
import org.gradle.internal.impldep.com.google.gson.JsonArray;
import org.gradle.internal.impldep.com.google.gson.JsonElement;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadMCPConfigTask extends DefaultTask {

    private static final String CONFIG_FILE_NAME = "config.json";

    private final Gson gson = new Gson();

    @InputFile
    public File configFile;
    @Input
    public String pipeline;

    public final RawMCPConfig rawConfig = new RawMCPConfig();

    @TaskAction
    public void validate() throws IOException {
        ZipFile zip = new ZipFile(configFile);

        ZipEntry configEntry = zip.getEntry(CONFIG_FILE_NAME);
        if (configEntry == null) {
            throw new IllegalStateException("Could not find '" + CONFIG_FILE_NAME + "' in " + configFile.getAbsolutePath());
        }

        InputStream configStream = zip.getInputStream(configEntry);
        JsonObject json = gson.fromJson(new InputStreamReader(configStream), JsonObject.class);
        configStream.close();

        zip.close();

        loadConfig(json, configFile);
    }

    private void loadConfig(JsonObject json, File zipFile) {
        int spec = json.get("spec").getAsInt();
        switch (spec) {
            case 1:
                loadConfigV1(json, zipFile);
                return;
            default:
                throw new IllegalStateException("Unsupported spec version '" + spec + "'.");
        }
    }

    private void loadConfigV1(JsonObject json, File zipFile) {
        rawConfig.mcVersion = json.get("version").getAsString();
        rawConfig.zipFile = zipFile;
        rawConfig.data = json.get("data").getAsJsonObject();

        JsonArray steps = json.get("steps").getAsJsonObject().get(pipeline).getAsJsonArray();
        boolean foundDecompile = false;
        for (JsonElement element : steps) {
            JsonObject step = element.getAsJsonObject();
            StepInfo stepInfo = readStep(step);
            if (foundDecompile |= stepInfo.type.equals("decompile")) { // If we find the decompilation task, switch to src-only
                rawConfig.pipeline.addSrc(stepInfo.name, stepInfo.type, stepInfo.arguments);
            } else {
                rawConfig.pipeline.addShared(stepInfo.name, stepInfo.type, stepInfo.arguments);
            }
        }

        JsonObject functions = json.get("functions").getAsJsonObject();
        for (Map.Entry<String, JsonElement> element : functions.entrySet()) {
            String name = element.getKey();
            JsonObject function = element.getValue().getAsJsonObject();

            String version = function.get("version").getAsString();
            String repo = function.get("repo").getAsString();
            String[] runArgs = function.has("args") ? toStringArray(function.get("args").getAsJsonArray()) : new String[0];
            String[] jvmArgs = function.has("jvmargs") ? toStringArray(function.get("jvmargs").getAsJsonArray()) : new String[0];
            Map<String, String> envVars = new HashMap<>();
            if (function.has("envvars")) {
                for (Map.Entry<String, JsonElement> entry : function.getAsJsonObject("envvars").entrySet()) {
                    envVars.put(entry.getKey(), entry.getValue().getAsString());
                }
            }

            rawConfig.addFunction(name, version, repo, jvmArgs, runArgs, envVars);
        }
    }

    private StepInfo readStep(JsonObject step) {
        String name = null;
        String type = null;
        Map<String, String> arguments = new HashMap<>();

        for (Map.Entry<String, JsonElement> element : step.entrySet()) {
            switch (element.getKey()) {
                case "name":
                    name = element.getValue().getAsString();
                    break;
                case "type":
                    type = element.getValue().getAsString();
                    break;
                default:
                    arguments.put(element.getKey(), element.getValue().getAsString());
                    break;
            }
        }

        if (name == null) {
            name = type;
        }

        return new StepInfo(name, type, arguments);
    }

    private String[] toStringArray(JsonArray array) {
        String[] arr = new String[array.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = array.get(i).getAsString();
        }
        return arr;
    }

    private class StepInfo {

        private final String name;
        private final String type;
        private final Map<String, String> arguments;

        public StepInfo(String name, String type, Map<String, String> arguments) {
            this.name = name;
            this.type = type;
            this.arguments = arguments;
        }

    }
}

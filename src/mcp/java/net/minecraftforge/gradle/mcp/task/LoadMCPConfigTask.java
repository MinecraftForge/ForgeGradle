package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.mcp.util.RawMCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Suppliers;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class LoadMCPConfigTask extends DefaultTask {

    private static final String CONFIG_FILE_NAME = "config.json";

    private final Gson gson = new Gson();

    private Supplier<File> _configFile;
    @InputFile public File getConfigFile() { return this._configFile.get(); }
    public void setConfigFile(File value) { this._configFile = () -> value; }
    public void setConfigFile(Supplier<File> value) { this._configFile = Suppliers.memoize(value::get); }

    private String _pipeline;
    @Input public String getPipeline() { return this._pipeline; }
    public void setPipeline(String value) { this._pipeline = value; }

    public final RawMCPConfig rawConfig = new RawMCPConfig(); //TODO: This is not cacheable as there is no output?

    @TaskAction
    public void validate() throws IOException {
        JsonObject json = null;
        try (ZipFile zip = new ZipFile(getConfigFile())) {

            ZipEntry configEntry = zip.getEntry(CONFIG_FILE_NAME);
            if (configEntry == null) {
                throw new IllegalStateException("Could not find '" + CONFIG_FILE_NAME + "' in " + getConfigFile().getAbsolutePath());
            }

            try (InputStream configStream = zip.getInputStream(configEntry)) {
                json = gson.fromJson(new InputStreamReader(configStream), JsonObject.class);
            }
        }

        if (json != null) {
            loadConfig(json, getConfigFile());
        }
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
        rawConfig.data.entrySet().stream().map(Map.Entry::getKey).forEach(key -> {
            if (rawConfig.data.get(key).isJsonObject() && rawConfig.data.get(key).getAsJsonObject().has(getPipeline())) {
                rawConfig.data.add(key, rawConfig.data.get(key).getAsJsonObject().get(getPipeline()));
            }
        });

        JsonArray steps = json.get("steps").getAsJsonObject().get(getPipeline()).getAsJsonArray();
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

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

class DownloadVersionJSONFunction extends DownloadFileFunction {

    private static final String DEFAULT_OUTPUT = "version.json";

    public DownloadVersionJSONFunction() {
        super(env -> DEFAULT_OUTPUT, DownloadVersionJSONFunction::getDownloadInfo);
    }

    private static DownloadInfo getDownloadInfo(MCPEnvironment environment) {
        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput("downloadManifest"));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            // Look for the version we want and return its URL
            for (JsonElement e : json.getAsJsonArray("versions")) {
                String v = e.getAsJsonObject().get("id").getAsString();
                if (v.equals(environment.getMinecraftVersion().toString())) {
                    return new DownloadInfo(e.getAsJsonObject().get("url").getAsString(), null,"json", v, null);
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return null;
    }

}

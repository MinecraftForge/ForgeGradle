/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

class DownloadCoreFunction extends DownloadFileFunction {
    DownloadCoreFunction(String artifact, String ext) {
        super(env -> artifact + '.' + ext, env -> getDownloadInfo(env, artifact, ext));
    }

    private static DownloadInfo getDownloadInfo(MCPEnvironment environment, String artifact, String extension) {
        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput("downloadJson"));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            JsonObject artifactInfo = json.getAsJsonObject("downloads").getAsJsonObject(artifact);
            String url = artifactInfo.get("url").getAsString();
            String hash = artifactInfo.get("sha1").getAsString();
            String version = json.getAsJsonObject().get("id").getAsString();
            return new DownloadInfo(url, hash, extension, version, artifact);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}

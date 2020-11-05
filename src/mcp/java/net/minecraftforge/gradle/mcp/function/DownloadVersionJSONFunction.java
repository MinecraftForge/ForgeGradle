/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.mcp.function;

import com.google.common.io.Files;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public class DownloadVersionJSONFunction extends AbstractFileDownloadFunction {

    private static final String DEFAULT_OUTPUT = "version.json";

    public DownloadVersionJSONFunction() {
        super(env -> DEFAULT_OUTPUT, DownloadVersionJSONFunction::getDownloadInfo);
    }

    private static DownloadInfo getDownloadInfo(MCPEnvironment environment) {
        try {
            JsonObject json = Utils.loadJson(environment.getStepOutput(DownloadManifestFunction.class));

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

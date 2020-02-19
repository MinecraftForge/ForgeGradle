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

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;

public class ListLibrariesFunction implements MCPFunction {

    @Override
    public File execute(MCPEnvironment environment) {
        File output = (File)environment.getArguments().computeIfAbsent("output", (key) -> environment.getFile("libraries.txt"));

        try {
            Gson gson = new Gson();
            Reader reader = new FileReader(environment.getStepOutput(DownloadVersionJSONFunction.class));
            JsonObject json = gson.fromJson(reader, JsonObject.class);
            reader.close();

            // Gather all the libraries
            Set<File> files = new HashSet<>();
            for (JsonElement libElement : json.getAsJsonArray("libraries")) {
                JsonObject library = libElement.getAsJsonObject();
                String name = library.get("name").getAsString();
                List<String> lst = new ArrayList<>();

                if (library.has("downloads")) {
                    JsonObject downloads = library.get("downloads").getAsJsonObject();
                    if (downloads.has("artifact"))
                        lst.add(name);
                    if (downloads.has("classifiers"))
                        downloads.get("classifiers").getAsJsonObject().keySet().forEach(cls -> lst.add(name + ':' + cls));
                }


                for (String artifact : lst) {
                    File lib = MavenArtifactDownloader.gradle(environment.project, artifact, false);
                    if (lib == null)
                        throw new RuntimeException("Could not resolve download: " + artifact);

                    files.add(lib);
                }
            }

            // Write the list
            if (output.exists()) output.delete();
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8));
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

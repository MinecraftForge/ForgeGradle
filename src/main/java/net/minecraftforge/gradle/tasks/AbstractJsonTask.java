/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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
package net.minecraftforge.gradle.tasks;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import groovy.lang.Closure;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public abstract class AbstractJsonTask<T extends AbstractJsonTask.IModInfo> extends DefaultTask {

    public interface IModInfo {
        void validate() throws InvalidUserDataException;
    }

    private String fileName;
    private T json;
    private Closure<?> transform;

    public AbstractJsonTask()
    {
        // never up to date
        this.getOutputs().upToDateWhen(Specs.satisfyNone());
    }

    @OutputFile
    public File getOutputFile() {
        return new File(getTemporaryDir(), fileName);
    }

    @TaskAction
    public void doTask() throws IOException {
        Gson gson = withGsonBuilder(new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Double.class, new JsonSerializer<Double>() {
                    @Override
                    public JsonElement serialize(Double src, Type typeOfSrc, JsonSerializationContext context) {
                        // demote the double if it has no decimal
                        if (src.longValue() == src)
                            return new JsonPrimitive(src.longValue());
                        return new JsonPrimitive(src);
                    }
                })).create();

        T json = getJson();
        json.validate();

        String object = gson.toJson(json);

        // data will be changed as a map
        Map map = gson.fromJson(object, Map.class);

        // allow the transformer to change the data
        if (this.transform != null) {
            ConfigureUtil.configure(this.transform, map);
        }

        // write to file
        FileWriter writer = null;
        try {
            File outputFile = this.getOutputFile();
            outputFile.delete();
            writer = new FileWriter(outputFile);

            gson.toJson(map, writer);

            writer.flush();
        } finally {
            IOUtils.closeQuietly(writer);
        }
    }

    private JsonObject merge(JsonObject base, JsonObject toMerge) {
        for (Map.Entry<String, JsonElement> e : toMerge.entrySet()) {
            String name = e.getKey();
            JsonElement element = e.getValue();
            if (base.has(name) && !element.isJsonPrimitive()) {
                JsonElement toReplace = base.get(name);

                // TODO merge objects/arrays

            } else {
                // doesn't exist or is a primitive
                base.add(name, element);
            }
        }
        return base;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public T getJson() {
        if (this.json == null) {
            this.json = createJson();
        }
        return this.json;
    }

    public void transform(Closure cl) {
        this.transform = cl;
    }

    public void json(Closure<?> configureClosure) {
        ConfigureUtil.configure(configureClosure, this.getJson());
    }

    protected GsonBuilder withGsonBuilder(GsonBuilder gson) {
        return gson;
    }

    protected abstract T createJson();
}

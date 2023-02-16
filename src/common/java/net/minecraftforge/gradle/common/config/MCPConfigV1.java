/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.config;

import net.minecraftforge.gradle.common.util.Utils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public class MCPConfigV1 extends Config {
    public static MCPConfigV1 get(InputStream stream) {
        return Utils.fromJson(stream, MCPConfigV1.class);
    }
    public static MCPConfigV1 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    protected String version; // Minecraft version
    @Nullable
    protected Map<String, Object> data;
    @Nullable
    protected Map<String, List<Step>> steps;
    @Nullable
    protected Map<String, Function> functions;
    @Nullable
    protected Map<String, List<String>> libraries;

    public String getVersion() {
        return version;
    }

    public Map<String, Object> getData() {
        return data == null ? Collections.emptyMap() : data;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public String getData(String... path) {
        if (data == null)
            return null;
        Map<String, Object> level = data;
        for (String part : path) {
            if (!level.containsKey(part))
                return null;
            Object val = level.get(part);
            if (val instanceof String)
                return (String)val;
            if (val instanceof Map)
                level = (Map<String, Object>)val;
        }
        return null;
    }

    public List<Step> getSteps(String side) {
        List<Step> ret = steps == null ? null : steps.get(side);
        return ret == null ? Collections.emptyList() : ret;
    }

    @Nullable
    public Function getFunction(String name) {
        return functions == null ? null : functions.get(name);
    }

    public Map<String, Function> getFunctions() {
        return functions == null ? Collections.emptyMap() : functions;
    }

    public List<String> getLibraries(String side) {
        List<String> ret = libraries == null ? null : libraries.get(side);
        return ret == null ? Collections.emptyList() : ret;
    }

    public static class Step {
        private final String type;
        private final String name;
        @Nullable
        private final Map<String, String> values;

        private Step(String type, String name, @Nullable Map<String, String> values) {
            this.type = type;
            this.name = name;
            this.values = values;
        }

        public String getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public Map<String, String> getValues() {
            return values == null ? Collections.emptyMap() : values;
        }

        @Nullable
        public String getValue(String key) {
            return values == null ? null : values.get(key);
        }

        public static class Deserializer implements JsonDeserializer<Step> {
            @Override
            public Step deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject obj = json.getAsJsonObject();
                if (!obj.has("type"))
                    throw new JsonParseException("Could not parse step: Missing 'type'");
                String type = obj.get("type").getAsString();
                String name = obj.has("name") ? obj.get("name").getAsString() : type;
                Map<String, String> values = obj.entrySet().stream()
                        .filter(e -> !"type".equals(e.getKey()) && !"name".equals(e.getKey()))
                        .collect(Collectors.toMap(Entry::getKey, e -> e.getValue().getAsString()));
                return new Step(type, name, values);
            }
        }
    }

    public static class Function {
        protected String version; //Maven artifact for the jar to run
        @Nullable
        protected String repo; //Maven repo to download the jar from
        @Nullable
        protected List<String> args;
        @Nullable
        protected List<String> jvmargs;
        @Nullable
        private Integer java_version;

        public String getVersion() {
            return version;
        }
        public void setVersion(String value) {
            this.version = value;
        }

        public String getRepo() {
            return repo == null ? Utils.MOJANG_MAVEN : repo;
        }
        public void setRepo(String value) {
            this.repo = value;
        }

        public List<String> getArgs() {
            return args == null ? Collections.emptyList() : args;
        }
        public void setArgs(List<String> value) {
            this.args = value;
        }

        public List<String> getJvmArgs() {
            return jvmargs == null ? Collections.emptyList() : jvmargs;
        }
        public void setJvmArgs(List<String> value) {
            this.jvmargs = value;
        }

        @Nullable
        public Integer getJavaVersion() {
            return this.java_version;
        }
        public void setJavaVersion(Integer javaVersion) {
            this.java_version = javaVersion;
        }
    }
}

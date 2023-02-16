/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.config;

import net.minecraftforge.gradle.common.util.Utils;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

public class MCPConfigV2 extends MCPConfigV1 {
    public static MCPConfigV2 get(InputStream stream) {
        return Utils.fromJson(stream, MCPConfigV2.class);
    }
    public static MCPConfigV2 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    public static MCPConfigV2 getFromArchive(File path) throws IOException {
        try (ZipFile zip = new ZipFile(path)) {
            ZipEntry entry = zip.getEntry("config.json");
            if (entry == null)
                throw new IllegalStateException("Could not find 'config.json' in " + path.getAbsolutePath());

            byte[] data = IOUtils.toByteArray(zip.getInputStream(entry));
            int spec = Config.getSpec(data);
            MCPConfigV2 config = null;
            if (spec == 2 || spec == 3 || spec == 4)
                config = MCPConfigV2.get(data);
            if (spec == 1)
                config = new MCPConfigV2(MCPConfigV1.get(data));

            if (config == null)
                throw new IllegalStateException("Invalid MCP Config: " + path.getAbsolutePath() + " Unknown spec: " + spec);

            // Verify that java_version is only used on spec 4 or higher
            if (spec < 4 && config.functions != null) {
                for (Function func : config.functions.values()) {
                    if (func.getJavaVersion() != null) {
                        throw new IllegalStateException("Invalid MCP Config: Function \"java_version\" property is only supported on spec 4 or higher, found spec: " + spec);
                    }
                }
            }

            return config;
        }
    }

    private boolean official = false;
    private int java_target = 8;
    @Nullable
    private String encoding = "UTF-8";

    public boolean isOfficial() {
        return this.official;
    }

    public int getJavaTarget() {
        return this.java_target;
    }

    public String getEncoding() {
        return this.encoding == null ? "UTF-8" : this.encoding;
    }

    public MCPConfigV2(MCPConfigV1 old) {
        this.version = old.version;
        this.data = old.data;
        this.steps = old.steps;
        this.functions = old.functions;
        this.libraries = old.libraries;
    }
}

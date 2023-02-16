/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.minecraftforge.gradle.common.util.Utils;

public class Config {
    public int spec;

    public static int getSpec(InputStream stream) throws IOException {
        return Utils.GSON.fromJson(new InputStreamReader(stream), Config.class).spec;
    }
    public static int getSpec(byte[] data) throws IOException {
        return getSpec(new ByteArrayInputStream(data));
    }
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import java.net.URL;

import javax.annotation.Nullable;

public class ManifestJson {
    public ManifestJson.VersionInfo[] versions;
    public static class VersionInfo {
        public String id;
        public URL url;
    }

    @Nullable
    public URL getUrl(@Nullable String version) {
        if (version == null) {
            return null;
        }
        for (VersionInfo info : versions) {
            if (version.equals(info.id)) {
                return info.url;
            }
        }
        return null;
    }
}

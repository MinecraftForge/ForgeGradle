package net.minecraftforge.gradle.common.util;

import java.net.URL;

public class ManifestJson {
    public ManifestJson.VersionInfo[] versions;
    public static class VersionInfo {
        public String id;
        public URL url;
    }

    public URL getUrl(String version) {
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

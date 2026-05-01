package net.minecraftforge.gradle.json;

import java.util.Date;
import java.util.List;

// format of https://piston-meta.mojang.com/mc/game/version_manifest.json
public class MCVersionManifest {
    public LatestInfo latest;
    public List<Version> versions;

    public Version findVersion(String versionId) {
        for (Version v : versions) {
            if (versionId.equals(v.id)) {
                return v;
            }
        }
        throw new IllegalArgumentException(versionId + " not found");
    }

    public static class LatestInfo {
        public String release;
        public String snapshot;
    }

    public static class Version {
        public String id;
        public String type;
        public String url;
        public Date time;
        public Date releaseTime;
    }
}

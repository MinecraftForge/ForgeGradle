package net.minecraftforge.gradle.common.util;

import java.net.URL;
import java.util.Locale;
import java.util.Map;

public class VersionJson {
    public AssetIndex assetIndex;
    public String assets;
    public Map<String, Download> downloads;
    public Library[] libraries;

    public static class AssetIndex extends Download {
        public String id;
        public int totalSize;
    }

    public static class Download {
        public String sha1;
        public int size;
        public URL url;
    }

    public static class LibraryDownload extends Download {
        public String path;
    }

    public static class Downloads {
        public Map<String, LibraryDownload> classifiers;
        public LibraryDownload artifact;
    }

    public static class Library {
        //Extract? rules?
        public String name;
        public Map<String, String> natives;
        public Downloads downloads;
        private Artifact _artifact;

        public Artifact getArtifact() {
            if (_artifact == null) {
                _artifact = new Artifact(name);
            }
            return _artifact;
        }
    }

    public static class Artifact {
        public final String group;
        public final String name;
        public final String version;
        public final String classifier;
        public final String ext;
        public final String path;

        public Artifact(String artifact) {
            int idx = artifact.indexOf('@');
            if (idx != -1) {
                ext = artifact.substring(idx + 1);
                artifact = artifact.substring(0, idx);
            } else {
                ext = "jar";
            }
            String[] pts = artifact.split(":");
            group = pts[0];
            name = pts[1];
            version = pts[2];
            classifier = pts.length > 3 ? pts[3] : null;

            StringBuilder buf = new StringBuilder();
            buf.append(group.replace('.', '/')).append('/')
            .append(name).append('/')
            .append(version).append('/')
            .append(name).append('-').append(version);

            if (classifier != null) {
                buf.append('-').append(classifier);
            }
            buf.append('.').append(ext);
            path = buf.toString();
        }
    }

    public static enum OS {
        WINDOWS("windows", "win"),
        LINUX("linux", "linux", "unix"),
        OSX("osx", "mac"),
        UNKNOWN("unknown");

        private final String name;
        private final String[] keys;

        private OS(String name, String... keys) {
            this.name = name;
            this.keys = keys;
        }

        public String getName() {
            return this.name;
        }

        public static OS getCurrent() {
            String prop = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            for (OS os : OS.values()) {
                for (String key : os.keys) {
                    if (prop.contains(key)) {
                        return os;
                    }
                }
            }
            return UNKNOWN;
        }
    }
}

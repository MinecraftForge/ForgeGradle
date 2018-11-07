/*
 * ForgeGradle
 * Copyright (C) 2018.
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

package net.minecraftforge.gradle.common.util;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VersionJson {
    public AssetIndex assetIndex;
    public String assets;
    public Map<String, Download> downloads;
    public Library[] libraries;

    private List<LibraryDownload> _natives = null;

    public List<LibraryDownload> getNatives() {
        if (_natives == null) {
            _natives = new ArrayList<>();
            OS os = OS.getCurrent();
            for (Library lib : libraries) {
                if (lib.natives != null && lib.downloads.classifiers != null && lib.natives.containsKey(os.getName())) {
                    LibraryDownload l = lib.downloads.classifiers.get(lib.natives.get(os.getName()));
                    if (l != null) {
                        _natives.add(l);
                    }
                }
            }
        }
        return _natives;
    }

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
                _artifact = Artifact.from(name);
            }
            return _artifact;
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

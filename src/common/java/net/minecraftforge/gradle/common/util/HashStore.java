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

package net.minecraftforge.gradle.common.util;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class HashStore {
    private final boolean INVALIDATE_CACHE = System.getProperty("FG_INVALIDATE_CACHE", "false").equals("true");
    private final int RAND_CACHE = new Random().nextInt();

    private final String root;
    private final Map<String, String> oldHashes = new HashMap<>();
    private final Map<String, String> newHashes = new HashMap<>();
    private File target;

    public HashStore() {
        this.root = "";
    }
    public HashStore(Project project) {
        this.root = project.getRootDir().getAbsolutePath();
    }
    public HashStore(File root) {
        this.root = root.getAbsolutePath();
    }

    public boolean areSame(File... files) {
        for(File file : files) {
            if(!isSame(file)) return false;
        }
        return true;
    }

    public boolean areSame(Iterable<File> files) {
        for(File file : files) {
            if(!isSame(file)) return false;
        }
        return true;
    }

    public boolean isSame(File file) {
        try {
            String path = getPath(file);
            String hash = oldHashes.get(path);
            if (hash == null) {
                if (file.exists()) {
                    newHashes.put(path, HashFunction.SHA1.hash(file));
                    return false;
                }
                return true;
            }
            HashUtil.sha1(file);
            String fileHash = HashFunction.SHA1.hash(file);
            newHashes.put(path, fileHash);
            return fileHash.equals(hash);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HashStore load(File file) throws IOException {
        this.target = file;
        oldHashes.clear();
        if(!file.exists()) return this;
        for (String line : FileUtils.readLines(file)) {
            String[] split = line.split("=");
            oldHashes.put(split[0], split[1]);
        }
        return this;
    }

    public boolean exists() {
        return this.target != null && this.target.exists();
    }

    public HashStore bust(int version) {
        newHashes.put("CACHE_BUSTER", Integer.toString(version));
        return this;
    }

    public HashStore add(String key, String data) {
        newHashes.put(key, HashFunction.SHA1.hash(data));
        return this;
    }

    public HashStore add(String key, byte[] data) {
        newHashes.put(key, HashFunction.SHA1.hash(data));
        return this;
    }

    public HashStore add(String key, File file) {
        try {
            newHashes.put(key == null ? getPath(file) : key, HashFunction.SHA1.hash(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    public HashStore add(File... files) {
        for (File file : files) {
            add(null, file);
        }
        return this;
    }
    public HashStore add(Iterable<File> files) {
        for (File file : files) {
            add(null, file);
        }
        return this;
    }
    public HashStore add(File file) {
        add(null, file);
        return this;
    }

    public boolean isSame() {
        if (INVALIDATE_CACHE)
            add("invalidate", "" + RAND_CACHE);
        return oldHashes.equals(newHashes);
    }

    public void save() throws IOException {
        if (target == null) {
            throw new RuntimeException("HashStore.save() called without load(File) so we dont know where to save it! Use load(File) or save(File)");
        }
        save(target);
    }
    public void save(File file) throws IOException {
        FileUtils.writeByteArrayToFile(file, newHashes.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("\n")).getBytes());
    }

    private String getPath(File file) {
        String path = file.getAbsolutePath();
        if (path.startsWith(root)) {
            return path.substring(root.length()).replace('\\', '/');
        } else {
            return path.replace('\\', '/');
        }
    }

}

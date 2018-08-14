package net.minecraftforge.gradle.common.util;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class HashStore {

    private final Project project;
    private final Map<String, HashValue> hashes = new HashMap<>();

    public HashStore(Project project) {
        this.project = project;
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
        String path = getPath(file);
        HashValue hash = hashes.get(path);
        if (hash == null) {
            if (file.exists()) {
                hashes.put(path, HashUtil.sha1(file));
                return false;
            }
            return true;
        }
        HashValue fileHash = HashUtil.sha1(file);
        hashes.put(path, fileHash);
        return fileHash.equals(hash);
    }

    public HashStore load(File file) throws IOException {
        hashes.clear();
        if(!file.exists()) return this;
        for (String line : FileUtils.readLines(file)) {
            String[] split = line.split("=");
            hashes.put(split[0], HashValue.parse(split[1]));
        }
        return this;
    }

    public void save(File file) throws IOException {
        Utils.createEmpty(file);
        PrintWriter pw = new PrintWriter(file);
        hashes.forEach((path, hash) -> pw.println(path + "=" + hash.asHexString()));
        pw.flush();
        pw.close();
    }

    private String getPath(File file) {
        int rootLength = project.getRootDir().getAbsolutePath().length();
        return file.getAbsolutePath().substring(rootLength).replace('\\', '/');
    }

}

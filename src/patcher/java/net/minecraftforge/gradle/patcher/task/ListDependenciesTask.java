package net.minecraftforge.gradle.patcher.task;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.Library;
import net.minecraftforge.gradle.common.util.VersionJson.LibraryDownload;
import net.minecraftforge.gradle.common.util.VersionJson.OS;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class ListDependenciesTask extends DefaultTask {
    private static final Gson GSON = new GsonBuilder().create();

    private boolean flat = true;
    private File versionMeta;
    private final Set<File> output = new HashSet<>();

    @TaskAction
    public void listDependencies() throws IOException {
        try (InputStream input = new FileInputStream(getVersionMeta())) {
            VersionJson json = GSON.fromJson(new InputStreamReader(input), VersionJson.class);

            Set<File> ret = new HashSet<>();

            if (flat) {
                for (Library lib : json.libraries) {
                    ret.add(downloadFlat(lib.downloads.artifact));
                    if (lib.natives != null) {
                        String key = lib.natives.get(OS.getCurrent().getName());
                        if (lib.downloads.classifiers != null && lib.downloads.classifiers.containsKey(key)) {
                            ret.add(downloadFlat(lib.downloads.classifiers.get(key)));
                        }
                    }
                }
            } else {
                for (Library lib : json.libraries) {
                    ret.addAll(MavenArtifactDownloader.download(getProject(), lib.name));
                }
            }

            output.addAll(ret);
        }
    }

    private File downloadFlat(LibraryDownload download) throws IOException {
        File target = Utils.getCache(getProject(), "libraries", download.path);
        File parent = target.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        if (target.exists()) {
            if (!HashFunction.SHA1.hash(target).equals(download.sha1)) {
                getProject().getLogger().lifecycle(HashFunction.SHA1.hash(target));
                getProject().getLogger().lifecycle(download.sha1);
                target.delete();
            } else {
                return target;
            }
        }

        getProject().getLogger().lifecycle("Downloading: " + download.url);
        FileUtils.copyURLToFile(download.url, target);
        return target;

    }

    @InputFile
    public File getVersionMeta() {
        return versionMeta;
    }

    public Set<File> getOutput() {
        return output;
    }

    public void setVersionMeta(File file) {
        this.versionMeta = file;
    }

}

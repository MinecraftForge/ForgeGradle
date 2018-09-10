package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;

public class TaskDownloadAssets extends DefaultTask {
    private static final String RESOURCE_REPO = "http://resources.download.minecraft.net/";
    private File meta;

    @TaskAction
    public void run() throws IOException {
        AssetIndex index = Utils.loadJson(getIndex(), AssetIndex.class);
        List<String> keys = new ArrayList<>(index.objects.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            Asset asset = index.objects.get(key);
            File target = Utils.getCache(getProject(), "assets/objects/", asset.getPath());
            if (!target.exists()) {
                URL url = new URL(RESOURCE_REPO + asset.getPath());
                getProject().getLogger().lifecycle("Downloading: " + url + " Asset: " + key);
                FileUtils.copyURLToFile(url, target);
            }
        }
    }

    private File getIndex() throws IOException {
        VersionJson json = Utils.loadJson(getMeta(), VersionJson.class);
        File target = Utils.getCache(getProject(), "assets/indexes/" + json.assetIndex.id + ".json");
        return Utils.updateDownload(getProject(), target, json.assetIndex);
    }

    @InputFile
    public File getMeta() {
        return this.meta;
    }
    public void setMeta(File value) {
        this.meta = value;
    }

    public File getOutput() {
        return Utils.getCache(getProject(), "assets/");
    }

    private static class AssetIndex {
        Map<String, Asset> objects;
    }
    private static class Asset {
        String hash;

        public String getPath() {
            return hash.substring(0, 2) + '/' + hash;
        }
    }
}

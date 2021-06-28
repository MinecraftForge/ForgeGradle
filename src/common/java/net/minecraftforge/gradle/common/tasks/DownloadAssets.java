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

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class DownloadAssets extends DefaultTask {

    /**
     * The Base URL that will be used to download Minecraft assets.
     * A trailing slash is required.
     */
    @Internal
    abstract Property<String> getAssetRepository();

    /**
     * Defines how many threads will be used to download assets concurrently.
     */
    @Internal
    abstract Property<Integer> getConcurrentDownloads();

    public DownloadAssets() {
        getAssetRepository().convention("https://resources.download.minecraft.net/");
        getConcurrentDownloads().convention(8);
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        AssetIndex index = Utils.loadJson(getIndex(), AssetIndex.class);
        List<String> keys = new ArrayList<>(index.objects.keySet());
        Collections.sort(keys);
        removeDuplicateRemotePaths(keys, index);

        File assetsPath = new File(Utils.getMCDir(), "/assets/objects");
        ExecutorService executorService = Executors.newFixedThreadPool(getConcurrentDownloads().get());
        CopyOnWriteArrayList<String> failedDownloads = new CopyOnWriteArrayList<>();
        for (String key : keys) {
            Asset asset = index.objects.get(key);
            File target = Utils.getCache(getProject(), "assets", "objects", asset.getPath());
            if (!target.exists() || !HashFunction.SHA1.hash(target).equals(asset.hash)) {
                URL url = new URL(getAssetRepository().get() + asset.getPath());
                Runnable copyURLtoFile = () -> {
                    try {
                        File localFile = FileUtils.getFile(assetsPath + File.separator + asset.getPath());
                        if (localFile.exists()) {
                            getProject().getLogger().lifecycle("Copying local object: " + asset.getPath() + " Asset: " + key);
                            FileUtils.copyFile(localFile, target);
                        } else {
                            getProject().getLogger().lifecycle("Downloading: " + url + " Asset: " + key);
                            FileUtils.copyURLToFile(url, target, 10_000, 5_000);
                        }
                        if (!HashFunction.SHA1.hash(target).equals(asset.hash)) {
                            failedDownloads.add(key);
                            Utils.delete(target);
                            getProject().getLogger().error("{} Hash failed.", key);
                        }
                    } catch (IOException e) {
                        failedDownloads.add(key);
                        getProject().getLogger().error("{} Failed.", key);
                        e.printStackTrace();
                    }
                };
                executorService.execute(copyURLtoFile);
            }
        }
        executorService.shutdown();
        executorService.awaitTermination(8, TimeUnit.HOURS);
        if (!failedDownloads.isEmpty()) {
            String errorMessage = "";
            for (String key : failedDownloads) {
                errorMessage += "Failed to get asset: " + key + "\n";
            }
            errorMessage += "Some assets failed to download or validate, try running the task again.";
            throw new RuntimeException(errorMessage);
        }
    }

    // Some keys may reference the same remote file. Remove these duplicates to prevent two threads
    // writing to the same file on disk.
    private static void removeDuplicateRemotePaths(List<String> keys, AssetIndex index) {
        Set<String> seen = new HashSet<>(keys.size());
        keys.removeIf(key -> !seen.add(index.objects.get(key).getPath()));
    }

    private File getIndex() throws IOException {
        VersionJson json = Utils.loadJson(getMeta().get().getAsFile(), VersionJson.class);
        File target = Utils.getCache(getProject(), "assets", "indexes", json.assetIndex.id + ".json");
        return Utils.updateDownload(getProject(), target, json.assetIndex);
    }

    @InputFile
    public abstract RegularFileProperty getMeta();

    @OutputDirectory
    public File getOutput() {
        return Utils.getCache(getProject(), "assets");
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

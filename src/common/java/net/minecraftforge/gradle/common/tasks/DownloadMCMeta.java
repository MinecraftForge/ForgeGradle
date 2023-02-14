/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.ManifestJson;

import net.minecraftforge.gradle.common.util.MinecraftRepo;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

public abstract class DownloadMCMeta extends DefaultTask {
    // TODO: convert this into a property?
    private static final String MANIFEST_URL = MinecraftRepo.MANIFEST_URL;
    private static final Gson GSON = new GsonBuilder().create();

    public DownloadMCMeta() {
        getManifest().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("manifest.json")));
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("version.json")));
    }

    @TaskAction
    public void downloadMCMeta() throws IOException {
        try (InputStream manin = new URL(MANIFEST_URL).openStream()) {
            URL url = GSON.fromJson(new InputStreamReader(manin), ManifestJson.class).getUrl(getMCVersion().get());
            if (url != null) {
                FileUtils.copyURLToFile(url, getOutput().get().getAsFile());
            } else {
                throw new RuntimeException("Missing version from manifest: " + getMCVersion().get());
            }
        }
    }

    @Input
    public abstract Property<String> getMCVersion();

    // TODO: check for uses, remove if not used
    @Internal
    public abstract RegularFileProperty getManifest();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

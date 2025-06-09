/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.util.download.DownloadUtils;
import net.minecraftforge.util.hash.HashStore;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static net.minecraftforge.gradle.ForgeGradlePlugin.LOGGER;

enum DefaultTools {
    MINECRAFT_MAVEN("minecraft-maven-" + Constants.MCMAVEN_VERSION + ".jar", Constants.MCMAVEN_DL_URL),
    SLIME_LAUNCHER("slime-launcher-" + Constants.SL_VERSION + ".jar", Constants.SL_DL_URL);

    private final String fileName;
    private final String downloadUrl;

    private static @UnknownNullability DirectoryProperty caches;

    DefaultTools(String fileName, String downloadUrl) {
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
    }

    public Provider<File> get(DirectoryProperty cachesDir, ProviderFactory providers) {
        return providers.of(Source.class, spec -> spec.parameters(parameters -> {
            parameters.getInputFile().set(cachesDir.file("tools/" + this.fileName));
            parameters.getDownloadUrl().set(this.downloadUrl);
        }));
    }

    static abstract class Source implements ValueSource<File, Source.Parameters> {
        interface Parameters extends ValueSourceParameters {
            @InputFile @PathSensitive(PathSensitivity.ABSOLUTE) RegularFileProperty getInputFile();

            @Input Property<String> getDownloadUrl();
        }

        @Inject
        public Source() { }

        @Override
        public File obtain() {
            var parameters = this.getParameters();

            // inputs
            var downloadUrl = parameters.getDownloadUrl().get();

            // outputs
            var outFile = parameters.getInputFile().get().getAsFile();
            var name = outFile.getName();

            // in-house caching
            HashStore cache = HashStore.fromFile(outFile)
                                       .add("tool", outFile)
                                       .add("url", downloadUrl);

            if (outFile.exists() && cache.isSame()) {
                LOGGER.info("Default tool already downloaded: {}", name);
            } else {
                LOGGER.info("Downloading default tool: {}", name);
                try {
                    DownloadUtils.downloadFile(outFile, downloadUrl);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to download default tool: " + name, e);
                }

                cache.add("tool", outFile).save();
            }

            return outFile;
        }
    }
}

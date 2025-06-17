/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.util.download.DownloadUtils;
import net.minecraftforge.util.hash.HashStore;
import org.gradle.api.Project;
import org.gradle.api.artifacts.UnknownConfigurationException;
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

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;

import static net.minecraftforge.gradle.ForgeGradlePlugin.LOGGER;

enum Tools {
    MINECRAFT_MAVEN("minecraft-maven-" + Constants.MCMAVEN_VERSION + ".jar", Constants.MCMAVEN_DL_URL, "mavenizer"),
    SLIME_LAUNCHER("slime-launcher-" + Constants.SL_VERSION + ".jar", Constants.SL_DL_URL, "slimelauncher");

    private final String fileName;
    private final String downloadUrl;
    private final String configuration;

    Tools(String fileName, String downloadUrl, String configuration) {
        this.fileName = fileName;
        this.downloadUrl = downloadUrl;
        this.configuration = configuration;
    }

    /// Gets a provider for this tool using the given caches directory and provider factory.
    ///
    /// @param cachesDir The caches directory to store the tool
    /// @param providers The provider factory to use
    /// @return A provider for the tool as a [file][File]
    /// @deprecated Use [ForgeGradlePlugin#getTool(Tools)] <- [org.gradle.api.plugins.PluginContainer#getPlugin(Class)]
    ///  <- [org.gradle.api.plugins.PluginAware#getPlugins()]
    @Deprecated
    Provider<File> get(DirectoryProperty cachesDir, ProviderFactory providers) {
        return providers.of(Source.class, spec -> spec.parameters(parameters -> {
            parameters.getInputFile().set(cachesDir.file("tools/" + this.fileName));
            parameters.getDownloadUrl().set(this.downloadUrl);
        }));
    }

    /// Gets a provider for this tool using the configuration, otherwise default to {@link Tools#get(Project, DirectoryProperty, ProviderFactory)}
    ///
    /// @param project The Project
    /// @param cachesDir The caches directory to store the tool
    /// @param providers The provider factory to use
    /// @return A provider for the tool as a [file][File]
    /// @deprecated Use [ForgeGradlePlugin#getTool(Tools, Project)] <- [org.gradle.api.plugins.PluginContainer#getPlugin(Class)]
    ///  <- [org.gradle.api.plugins.PluginAware#getPlugins()]
    @Deprecated
    Provider<File> get(Project project, DirectoryProperty cachesDir, ProviderFactory providers) {
        try {
            final var cfg = project.getConfigurations().getByName(getConfiguration());
            final var file = cfg.getSingleFile();
            return providers.provider(() -> file);
        } catch (UnknownConfigurationException exception) {
            return get(cachesDir, providers);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException(String.format("Cant have more then one %s define", getConfiguration()));
        }
    }

    String getConfiguration() {
        return configuration;
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

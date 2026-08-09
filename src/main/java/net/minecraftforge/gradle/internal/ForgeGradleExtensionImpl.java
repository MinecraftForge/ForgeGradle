/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.ForgeGradleExtensionForProject;
import net.minecraftforge.util.hash.HashFunction;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.reflect.TypeOf;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.github.jezza.Toml;
import com.github.jezza.TomlArray;
import com.github.jezza.TomlTable;
import org.jspecify.annotations.Nullable;

abstract class ForgeGradleExtensionImpl implements ForgeGradleExtensionInternal {
    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        var extensions = target.getExtensions();
        if (target instanceof Project project) {
            extensions.create(ForgeGradleExtensionForProject.class, ForgeGradleExtension.NAME, ForgeGradleExtensionImpl.ForProjectImpl.class, plugin, project);
        } else {
            extensions.create(ForgeGradleExtension.class, ForgeGradleExtension.NAME, ForgeGradleExtensionImpl.class);
        }
    }



    @Inject
    public ForgeGradleExtensionImpl() { }

    @Override
    public TypeOf<?> getPublicType() {
        return ForgeGradleExtensionInternal.super.getPublicType();
    }

    static abstract class ForProjectImpl extends ForgeGradleExtensionImpl implements ForgeGradleExtensionForProject {
        private final ForgeGradlePlugin plugin;
        private final Project project;

        @Inject
        public ForProjectImpl(ForgeGradlePlugin plugin, Project project) {
            this.plugin = plugin;
            this.project = project;
        }

        @Override
        public FileCollection findFiles(Object files, String name) {
            return findFiles(this.project.files(files), name);
        }

        @Override
        public FileCollection findFiles(FileCollection files, String name) {
            return findFilesInArchives(files, (file, jar) -> List.of(jar.getEntry(name)));
        }

        @Override
        public FileCollection findAccessTransformers(Object files) {
            return findAccessTransformers(this.project.files(files));
        }

        @Override
        public FileCollection findAccessTransformers(FileCollection files) {
            return findFilesInArchives(files, this::findAccessTransformer);
        }

        @Override
        public FileCollection findFacades(Object files) {
            return findFacades(this.project.files(files));
        }

        @Override
        public FileCollection findFacades(FileCollection files) {
            return findFilesInArchives(files, this::findFacades);
        }

        private FileCollection findFilesInArchives(FileCollection files, BiFunction<File, JarFile, List<? extends ZipEntry>> filter) {
            var root = this.plugin.localCaches().dir("zip_data");
            return this.project.files(this.project.provider(() -> {
                var ret = new ArrayList<File>();
                for (var file : files) {
                    var fileName = file.getName().toLowerCase(Locale.ENGLISH);
                    if (!fileName.endsWith(".zip") && !fileName.endsWith(".jar"))
                        continue;

                    // We use jar file so that we can grab the manifest, for zip files it'll just be an empty manifest
                    try (var jar = new JarFile(file)) {
                        var entries = filter.apply(file, jar);
                        if (entries == null || entries.isEmpty())
                            continue;

                        var hash = HashFunction.SHA1.hash(file);
                        var prefix = file.getName().substring(0, file.getName().length() - 4);
                        var dir = root.map(d -> d.dir(hash).dir(prefix)).get().getAsFile().getAbsoluteFile();

                        for (var entry : entries) {
                            var name = entry.getName();
                            var target = new File(dir, name.replace('/', File.separatorChar)).getAbsoluteFile();
                            //this.project.getLogger().lifecycle("Root: " + dir.toString() + File.separatorChar);
                            //this.project.getLogger().lifecycle("      " + target.toString());
                            if (!target.toString().startsWith(dir.toString() + File.separatorChar))
                                throw new IllegalArgumentException("Invalid file " + name);

                            // We've already extracted, assume it's correct. Could check hash, but we do for the input file, so should be fine.
                            // This is also not thread safe, but that's more work than I think we need.
                            if (!target.exists()) {
                                var parent = target.getParentFile();
                                if (!parent.exists() && !parent.mkdirs())
                                    throw new IllegalStateException("Could not create directory " + parent.getAbsolutePath());
                                try (var input = jar.getInputStream(entry)) {
                                    Files.copy(input, target.toPath());
                                }
                            }
                            ret.add(target);
                        }
                    }
                }
                return ret;
            }));
        }


        private static final String MODS_TOML = "META-INF/mods.toml";
        private static final String ACCESS_TRANSFORMER = "META-INF/accesstransformer.cfg";
        private static final java.util.jar.Attributes.Name FMLAT = new java.util.jar.Attributes.Name("FMLAT");

        private List<ZipEntry> findAccessTransformer(File file, JarFile jar) {
            var ret = new ArrayList<ZipEntry>();

            boolean hasCustom = false;
            var configs = manifest(file, jar, FMLAT);
            if (configs != null) {
                for (var cfg : configs)
                    add(ret, jar, "META-INF/" + cfg);
                hasCustom = true;
            }

            var tomlEntry = jar.getEntry(MODS_TOML);
            if (tomlEntry != null) {
                try (var input = jar.getInputStream(tomlEntry)) {
                    TomlTable toml = Toml.from(input);
                    if (toml.get("mods") instanceof TomlArray mods) {
                        for (Object mod : mods) {
                            if (mod instanceof TomlTable table) {
                                Object ats = table.get("accessTransformers");
                                if (ats instanceof String string) {
                                    if (!string.isEmpty()) {
                                        for (var at : string.split(","))
                                            add(ret, jar, at);
                                    }
                                    hasCustom = true;
                                } else if (ats instanceof TomlArray array) {
                                    for (Object at : array)
                                        add(ret, jar, (String)at);
                                    hasCustom = true;
                                }
                            }
                        }
                    }
                } catch (IOException | ClassCastException e) {
                    this.project.getLogger().info("Failed to parse mods.toml from {}", file.getAbsolutePath(), e);
                }
            }

            if (!hasCustom)
                add(ret, jar, ACCESS_TRANSFORMER);

            return ret;
        }

        private static final java.util.jar.Attributes.Name FORGE_FACADE = new java.util.jar.Attributes.Name("FORGE_FACADE");
        private static final String FACADES = "META-INF/facades.cfg";
        private List<ZipEntry> findFacades(File file, JarFile jar) {
            var ret = new ArrayList<ZipEntry>();

            var configs = manifest(file, jar, FORGE_FACADE);
            if (configs != null) {
                for (var cfg : configs)
                    add(ret, jar, "META-INF/" + cfg);
            } else {
                add(ret, jar, FACADES);
            }

            return ret;
        }

        private String @Nullable[] manifest(File file, JarFile jar, java.util.jar.Attributes.Name name) {
            try {
                var configs = (String)jar.getManifest().getMainAttributes().get(name);
                if (configs == null)
                    return null;
                return configs.isEmpty() ? new String[0] : configs.split(" ");
            } catch (IOException e) {
                this.project.getLogger().warn("Unable to read MANIFEST.MF file: {}", file.getAbsolutePath(), e);
                return null;
            }
        }

        private static void add(List<ZipEntry> ret, JarFile jar, String path) {
            var entry = jar.getEntry(path);
            if (entry != null)
                ret.add(entry);
        }
    }
}

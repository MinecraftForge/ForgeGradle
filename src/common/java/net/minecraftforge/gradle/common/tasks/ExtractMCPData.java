/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.config.MCPConfigV2;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class ExtractMCPData extends DefaultTask {
    private boolean allowEmpty = false;

    public ExtractMCPData() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("output.srg")));
        getKey().convention("mappings");
    }

    @TaskAction
    public void run() throws IOException {
        MCPConfigV2 cfg = MCPConfigV2.getFromArchive(getConfig().get().getAsFile());

        String key = getKey().get();
        File output = getOutput().get().getAsFile();
        try (ZipFile zip = new ZipFile(getConfig().get().getAsFile())) {
            String path = cfg.getData(key.split("/"));
            if (path == null && "statics".equals(key))
                path = "config/static_methods.txt";

            if (path == null) {
                error("Could not find data entry for '" + key + "'");
                return;
            }

            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                error("Invalid config zip, Missing path '" + path + "'");
                return;
            }

            try (OutputStream out = new FileOutputStream(output)) {
                IOUtils.copy(zip.getInputStream(entry), out);
            }
        }

        if (cfg.isOfficial() && output.exists() && "mappings".equals(key)) {
            IMappingFile obfToSrg = IMappingFile.load(output);
            remapSrgClasses(getProject(), cfg, obfToSrg).write(output.toPath(), IMappingFile.Format.TSRG2, false);
        }
    }

    public static IMappingFile remapSrgClasses(Project project, MCPConfigV2 config, IMappingFile obfToSrg) throws IOException {
        String minecraftVersion = MinecraftRepo.getMCVersion(config.getVersion());
        File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + minecraftVersion + ":mappings@txt", true);

        IMappingFile obfToOfficial = IMappingFile.load(client).reverse();

        return obfToSrg.rename(new IRenamer() {
            @Override
            public String rename(IMappingFile.IClass value) {
                return obfToOfficial.remapClass(value.getOriginal());
            }
        });
    }

    private void error(String message) throws IOException {
        if (!isAllowEmpty())
            throw new IllegalStateException(message);

        File outputFile = getOutput().get().getAsFile();
        if (outputFile.exists())
            outputFile.delete();

        outputFile.createNewFile();
    }

    @Input
    public abstract Property<String> getKey();

    @InputFile
    public abstract RegularFileProperty getConfig();

    @Input
    public boolean isAllowEmpty() {
        return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
        this.allowEmpty = allowEmpty;
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

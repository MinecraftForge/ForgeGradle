/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class RenameJarInPlace extends JarExec {
    private final Provider<RegularFile> tempOutput = this.workDir.map(s -> s.file("output.jar"));
    private final Provider<RegularFile> tempMappings = this.workDir.map(s -> s.file("mappings.tsrg"));

    public RenameJarInPlace() {
        getTool().set(Utils.FART);
        getArgs().addAll("--input", "{input}", "--output", "{output}", "--names", "{mappings}", "--lib", "{libraries}");
        this.getOutputs().upToDateWhen(task -> false);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgsMulti(args, ImmutableMap.of(
                        "{input}", getInput().get().getAsFile(),
                        "{output}", tempOutput.get().getAsFile(),
                        "{mappings}", this.tempMappings.get().getAsFile()),
                ImmutableMultimap.<String, Object>builder()
                        .putAll("{libraries}", getLibraries().getFiles())
                        .build()
        );
    }

    @Override
    @TaskAction
    public void apply() throws IOException {
        File tempJar = this.tempOutput.get().getAsFile();
        File tempMappings = this.tempMappings.get().getAsFile();

        if (tempJar.getParentFile() != null && !tempJar.getParentFile().exists() && !tempJar.getParentFile().mkdirs()) {
            getProject().getLogger().warn("Could not create parent directories for temp dir '{}'", tempJar.getAbsolutePath());
        }

        if (tempMappings.exists() && !tempMappings.delete())
            throw new IllegalStateException("Could not delete temp mappings file: " + tempMappings.getAbsolutePath());

        IMappingFile mappings = IMappingFile.load(getMappings().get().getAsFile());

        for (File file : getExtraMappings().getFiles()) {
            mappings = mappings.merge(IMappingFile.load(file));
        }

        mappings.write(tempMappings.toPath(), IMappingFile.Format.TSRG2, false);

        super.apply();

        FileUtils.copyFile(tempJar, getInput().get().getAsFile());
    }

    // TODO: Make this a ConfigurableFileCollection? (then remove getExtraMappings())
    @InputFile
    public abstract RegularFileProperty getMappings();

    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getExtraMappings();

    /**
     * The libraries to use for inheritance data during the renaming process.
     */
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @InputFile
    public abstract RegularFileProperty getInput();
}

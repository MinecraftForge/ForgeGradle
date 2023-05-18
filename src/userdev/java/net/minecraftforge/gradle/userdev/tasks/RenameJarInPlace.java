/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import org.apache.commons.io.FileUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
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
    private final Provider<Directory> workDir = getProject().getLayout().getBuildDirectory().dir(getName());
    private final Provider<RegularFile> temp = workDir.map(s -> s.file("output.jar"));

    public RenameJarInPlace() {
        getTool().set(Utils.FART);
        getArgs().addAll("--input", "{input}", "--output", "{output}", "--names", "{mappings}", "--lib", "{libraries}", "--ann-fix", "--ids-fix", "--src-fix", "--record-fix");
        this.getOutputs().upToDateWhen(task -> false);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgsMulti(args, ImmutableMap.of(
                        "{input}", getInput().get().getAsFile(),
                        "{output}", temp.get().getAsFile()),
                ImmutableMultimap.<String, Object>builder()
                        .put("{mappings}", getMappings().get().getAsFile())
                        .putAll("{mappings}", getExtraMappings().getFiles())
                        .putAll("{libraries}", getLibraries().getFiles())
                        .build()
        );
    }

    @Override
    @TaskAction
    public void apply() throws IOException {
        File temp = this.temp.get().getAsFile();
        if (temp.getParentFile() != null && !temp.getParentFile().exists() && !temp.getParentFile().mkdirs()) {
            getProject().getLogger().warn("Could not create parent directories for temp dir '{}'", temp.getAbsolutePath());
        }

        super.apply();

        FileUtils.copyFile(temp, getInput().get().getAsFile());
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

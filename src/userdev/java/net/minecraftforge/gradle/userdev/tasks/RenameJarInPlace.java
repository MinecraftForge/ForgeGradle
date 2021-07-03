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
        getTool().set(Utils.SPECIALSOURCE);
        getArgs().addAll("--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{mappings}", "--live");
        this.getOutputs().upToDateWhen(task -> false);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgsMulti(args, ImmutableMap.of(
                "{input}", getInput().get().getAsFile(),
                "{output}", temp.get().getAsFile()
                ), ImmutableMultimap.<String, Object>builder()
                        .put("{mappings}", getMappings().get().getAsFile())
                        .putAll("{mappings}", getExtraMappings().getFiles()).build()
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

    @InputFile
    public abstract RegularFileProperty getInput();
}

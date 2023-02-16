/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.gradle.common.util.Utils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import java.util.List;

public abstract class CheckJarCompatibility extends JarExec {
    public CheckJarCompatibility() {
        getTool().set(Utils.JARCOMPATIBILITYCHECKER);
        getArgs().addAll("--base-jar", "{base_jar}", "--input-jar", "{input_jar}", "--lib", "{lib}", "--base-lib", "{base_lib}", "--concrete-lib", "{concrete_lib}");

        getBinaryMode().convention(false);
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        List<String> newArgs = replaceArgs(args,
                ImmutableMap.of(
                        "{base_jar}", getBaseJar().get().getAsFile(),
                        "{input_jar}", getInputJar().get().getAsFile()
                ), ImmutableMap.of(
                        "{lib}", getCommonLibraries().getFiles(),
                        "{base_lib}", getBaseLibraries().getFiles(),
                        "{concrete_lib}", getConcreteLibraries().getFiles()
                ));

        if (getBinaryMode().get()) {
            newArgs.add("--binary");
        } else {
            newArgs.add("--api");
        }

        if (getAnnotationCheckMode().isPresent()) {
            newArgs.add("--annotation-check-mode");
            newArgs.add(getAnnotationCheckMode().get());
        }

        return newArgs;
    }

    @Input
    public abstract Property<Boolean> getBinaryMode();

    @Input
    @Optional
    public abstract Property<String> getAnnotationCheckMode();

    @InputFile
    public abstract RegularFileProperty getBaseJar();

    @InputFile
    public abstract RegularFileProperty getInputJar();

    @InputFiles
    public abstract ConfigurableFileCollection getCommonLibraries();

    @InputFiles
    public abstract ConfigurableFileCollection getBaseLibraries();

    @InputFiles
    public abstract ConfigurableFileCollection getConcreteLibraries();
}

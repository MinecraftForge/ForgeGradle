/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import net.minecraftforge.gradle.common.util.Utils;

import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class FilterNewJar extends DefaultTask { //TODO: Copy task?
    public FilterNewJar() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
    }

    @TaskAction
    public void apply() throws IOException {
        Set<String> filter = new HashSet<>();
        for (File file : getBlacklist()) {
            try (ZipFile zip = new ZipFile(file)) {
                Utils.forZip(zip, entry -> filter.add(entry.getName()));
            }
        }

        Set<String> classes = IMappingFile.load(getSrg().get().getAsFile()).getClasses().stream()
                .map(IMappingFile.IClass::getMapped)
                .collect(Collectors.toSet());

        try (ZipFile zin = new ZipFile(getInput().get().getAsFile());
             ZipOutputStream out = new ZipOutputStream(new FileOutputStream(getOutput().get().getAsFile()))){

            Utils.forZip(zin, entry -> {
                if (entry.isDirectory() || filter.contains(entry.getName()) ||
                        (entry.getName().endsWith(".class") && isVanilla(classes, entry.getName().substring(0, entry.getName().length() - 6)))) {
                    return;
                }
                out.putNextEntry(Utils.getStableEntry(entry.getName()));
                IOUtils.copy(zin.getInputStream(entry), out);
                out.closeEntry();
            });
        }
    }

    //We pack all inner classes in binpatches. So strip anything thats a vanilla class or inner class of one.
    private boolean isVanilla(Set<String> classes, String cls) {
        int idx = cls.indexOf('$');
        if (idx != -1) {
            return isVanilla(classes, cls.substring(0, idx));
        }
        return classes.contains(cls);
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getSrg();

    @InputFiles
    public abstract ConfigurableFileCollection getBlacklist();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

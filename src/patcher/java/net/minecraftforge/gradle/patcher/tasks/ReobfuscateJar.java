/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import com.google.common.collect.ImmutableMultimap;
import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.ImmutableMap;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public abstract class ReobfuscateJar extends JarExec {
    private boolean keepPackages = false;
    private boolean keepData = false;

    private final Provider<RegularFile> outputTemp = workDir.map(d -> d.file("output_temp.jar"));

    public ReobfuscateJar() {
        getTool().set(Utils.FART);
        getArgs().addAll("--input", "{input}", "--output", "{output}", "--names", "{srg}", "--lib", "{libraries}");
        getOutput().convention(workDir.map(d -> d.file("output.jar")));
    }

    @TaskAction
    public void apply() throws IOException {
        super.apply();

        try (OutputStream log = new BufferedOutputStream(Files.newOutputStream(logFile.get().getAsFile().toPath(), StandardOpenOption.WRITE, StandardOpenOption.APPEND))) {
            Set<String> packages = new HashSet<>();
            IMappingFile srgMappings = IMappingFile.load(getSrg().get().getAsFile());
            for (IMappingFile.IClass srgClass : srgMappings.getClasses()) {
                String named = srgClass.getOriginal();
                int idx = named.lastIndexOf('/');
                if (idx != -1) {
                    packages.add(named.substring(0, idx + 1) + "package-info.class");
                }
            }

            try (ZipFile zin = new ZipFile(outputTemp.get().getAsFile());
                 ZipOutputStream out = new ZipOutputStream(new FileOutputStream(getOutput().get().getAsFile()))) {
                for (Enumeration<? extends ZipEntry> enu = zin.entries(); enu.hasMoreElements(); ) {
                    ZipEntry entry = enu.nextElement();
                    boolean filter = entry.isDirectory() || entry.getName().startsWith("mcp/"); //Directories and MCP's annotations
                    if (!keepPackages) filter |= packages.contains(entry.getName());
                    if (!keepData) filter |= !entry.getName().endsWith(".class");

                    if (filter) {
                        log.write(("Filtered: " + entry.getName() + '\n').getBytes(StandardCharsets.UTF_8));
                        continue;
                    }
                    out.putNextEntry(entry);
                    IOUtils.copy(zin.getInputStream(entry), out);
                    out.closeEntry();
                }
            }

            outputTemp.get().getAsFile().delete();
        }
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgsMulti(args, ImmutableMap.of(
                        "{input}", getInput().get().getAsFile(),
                        "{output}", outputTemp.get().getAsFile(),
                        "{srg}", getSrg().get().getAsFile()),
                ImmutableMultimap.<String, Object>builder()
                        .putAll("{libraries}", getLibraries().getFiles())
                        .build());
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getSrg();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    /**
     * The libraries to use for inheritance data during the renaming process.
     */
    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getLibraries();

    @Input
    public boolean getKeepPackages() {
        return this.keepPackages;
    }

    public void keepPackages() {
        this.keepPackages = true;
    }

    public void filterPackages() {
        this.keepPackages = false;
    }

    @Input
    public boolean getKeepData() {
        return this.keepData;
    }

    public void keepData() {
        this.keepData = true;
    }

    public void filterData() {
        this.keepData = false;
    }
}

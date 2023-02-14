/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import java.nio.file.Path;

public abstract class GeneratePatches extends DefaultTask {
    private int contextLines = -1;
    private boolean autoHeader;
    private boolean verbose;
    private boolean printSummary;

    public GeneratePatches() {
        getOriginalPrefix().convention("a/");
        getModifiedPrefix().convention("b/");
        getLineEnding().convention(System.lineSeparator());
    }

    @TaskAction
    public void doTask() throws Exception {
        Path base = getBase().get().getAsFile().toPath();
        Path modified = getModified().get().getAsFile().toPath();
        Path output = getOutput().get().getAsFile().toPath();
        getProject().getLogger().info("Base: {}", base);
        getProject().getLogger().info("Modified: {}", modified);

        ArchiveFormat outputFormat = getOutputFormat().getOrNull();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(output.getFileName());
        }

        DiffOperation.Builder builder = DiffOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .aPath(base)
                .bPath(modified)
                .outputPath(output, outputFormat)
                .autoHeader(autoHeader)
                .verbose(verbose)
                .summary(printSummary)
                .aPrefix(getOriginalPrefix().get())
                .bPrefix(getModifiedPrefix().get())
                .lineEnding(getLineEnding().get());

        if (contextLines != -1) {
            builder.context(contextLines);
        }

        CliOperation.Result<DiffOperation.DiffSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
    }

    @InputFile
    public abstract RegularFileProperty getBase();

    @InputFile
    public abstract RegularFileProperty getModified();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @Input
    @Optional
    public abstract Property<ArchiveFormat> getOutputFormat();

    @Input
    @Optional
    public abstract Property<String> getOriginalPrefix();

    @Input
    @Optional
    public abstract Property<String> getModifiedPrefix();

    @Input
    public abstract Property<String> getLineEnding();

    @Input
    public boolean isAutoHeader() {
        return autoHeader;
    }

    public void setAutoHeader(boolean autoHeader) {
        this.autoHeader = autoHeader;
    }

    @Input
    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int lines) {
        this.contextLines = lines;
    }

    @Console
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Console
    public boolean isPrintSummary() {
        return printSummary;
    }

    public void setPrintSummary(boolean printSummary) {
        this.printSummary = printSummary;
    }
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import java.io.File;
import java.nio.file.Path;
import java.util.logging.Level;

public abstract class ApplyPatches extends DefaultTask {
    private float minFuzzQuality = -1;
    private int maxFuzzOffset = -1;
    private Level level = Level.WARNING;
    private boolean printSummary = false;
    private boolean failOnError = true;

    public ApplyPatches() {
        getPatchMode().convention(PatchMode.EXACT);
        getPatchesPrefix().convention("");
        getOriginalPrefix().convention("a/");
        getModifiedPrefix().convention("b/");
    }

    @TaskAction
    public void doTask() throws Exception {
        if (!getPatches().isPresent()) {
            FileUtils.copyFile(getBase().get(), getOutput().get().getAsFile());
            return;
        }

        Path outputPath = getOutput().get().getAsFile().toPath();
        ArchiveFormat outputFormat = getOutputFormat().getOrNull();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }

        Path rejectsPath = getRejects().map(File::toPath).getOrNull();
        ArchiveFormat rejectsFormat = getOutputFormat().getOrNull();
        if (rejectsFormat == null && rejectsPath != null) {
            rejectsFormat = ArchiveFormat.findFormat(rejectsPath.getFileName());
        }

        PatchOperation.Builder builder = PatchOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .basePath(getBase().get().toPath())
                .patchesPath(getPatches().get().getAsFile().toPath())
                .outputPath(outputPath, outputFormat)
                .rejectsPath(rejectsPath, rejectsFormat)
                .level(level)
                .summary(printSummary)
                .mode(getPatchMode().get())
                .aPrefix(getOriginalPrefix().get())
                .bPrefix(getModifiedPrefix().get())
                .patchesPrefix(getPatchesPrefix().get());
        if (minFuzzQuality != -1) {
            builder.minFuzz(minFuzzQuality);
        }
        if (maxFuzzOffset != -1) {
            builder.maxOffset(maxFuzzOffset);
        }

        CliOperation.Result<PatchOperation.PatchesSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
        if (exit != 0 && isFailOnError()) {
            throw new RuntimeException("Patches failed to apply.");
        }
    }

    // TODO: split into separate (exclusive) properties for directory or file?
    @InputFile
    public abstract Property<File> getBase();

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getPatches();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Internal
    public abstract Property<File> getRejects();

    @Input
    @Optional
    public abstract Property<ArchiveFormat> getOutputFormat();

    @Input
    @Optional
    public abstract Property<ArchiveFormat> getRejectsFormat();

    @Input
    @Optional
    public abstract Property<PatchMode> getPatchMode();

    @Input
    @Optional
    public abstract Property<String> getPatchesPrefix();

    @Input
    @Optional
    public abstract Property<String> getOriginalPrefix();

    @Input
    @Optional
    public abstract Property<String> getModifiedPrefix();

    @Input
    public float getMinFuzzQuality() {
        return minFuzzQuality;
    }

    public void setMinFuzzQuality(float minFuzzQuality) {
        this.minFuzzQuality = minFuzzQuality;
    }

    @Input
    public int getMaxFuzzOffset() {
        return maxFuzzOffset;
    }

    public void setMaxFuzzOffset(int maxFuzzOffset) {
        this.maxFuzzOffset = maxFuzzOffset;
    }

    @Console
    public boolean isVerbose() {
        return this.level == Level.ALL;
    }

    public void setVerbose(boolean verbose) {
        this.level = verbose ? Level.ALL : Level.WARNING;
    }

    @Console
    public Level getLevel() {
        return this.level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public void setLevel(String level) {
        this.setLevel(Level.parse(level));
    }

    @Console
    public boolean isPrintSummary() {
        return printSummary;
    }

    public void setPrintSummary(boolean printSummary) {
        this.printSummary = printSummary;
    }

    @Input
    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }
}

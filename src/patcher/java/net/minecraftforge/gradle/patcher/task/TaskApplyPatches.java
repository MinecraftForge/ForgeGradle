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

package net.minecraftforge.gradle.patcher.task;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.*;

import java.io.File;
import java.nio.file.Path;

public class TaskApplyPatches extends DefaultTask {

    private File base;
    private File patches;
    private File output;
    private File rejects;
    private ArchiveFormat outputFormat;
    private ArchiveFormat rejectsFormat;
    private float minFuzzQuality = -1;
    private int maxFuzzOffset = -1;
    private PatchMode patchMode = PatchMode.EXACT;
    private String patchesPrefix = "";
    private boolean verbose = false;
    private boolean printSummary = false;
    private boolean failOnError = true;

    private String originalPrefix = "a/";
    private String modifiedPrefix = "b/";

    @TaskAction
    public void doTask() throws Exception {
        if (patches == null) {
            FileUtils.copyFile(getBase(), getOutput());
            return;
        }

        Path outputPath = getOutput().toPath();
        ArchiveFormat outputFormat = getOutputFormat();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }

        Path rejectsPath = getRejects().toPath();
        ArchiveFormat rejectsFormat = getOutputFormat();
        if (rejectsFormat == null) {
            rejectsFormat = ArchiveFormat.findFormat(rejectsPath.getFileName());
        }

        PatchOperation.Builder builder = PatchOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .basePath(getBase().toPath())
                .patchesPath(getPatches().toPath())
                .outputPath(outputPath, outputFormat)
                .rejectsPath(rejectsPath, rejectsFormat)
                .verbose(isVerbose())
                .summary(isPrintSummary())
                .mode(getPatchMode())
                .aPrefix(originalPrefix)
                .bPrefix(modifiedPrefix)
                .patchesPrefix(getPatchesPrefix());
        float minFuzz = getMinFuzzQuality();
        int maxOffset = getMaxFuzzOffset();
        if (minFuzz != -1) {
            builder.minFuzz(minFuzz);
        }
        if (maxOffset != -1) {
            builder.maxOffset(maxOffset);
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

    //@formatter:off
    @Input                    public File getBase() { return base; }
    @InputDirectory @Optional public File getPatches() { return patches ; }
    @OutputFile               public File getOutput() { return output; }
                    @Optional public File getRejects() { return rejects; }
    @Input          @Optional public ArchiveFormat getOutputFormat() { return outputFormat; }
    @Input          @Optional public ArchiveFormat getRejectsFormat() { return rejectsFormat; }
    @Input          @Optional public float getMinFuzzQuality() { return minFuzzQuality; }
    @Input          @Optional public int getMaxFuzzOffset() { return maxFuzzOffset; }
    @Input          @Optional public PatchMode getPatchMode() { return patchMode; }
    @Input          @Optional public String getPatchesPrefix() { return patchesPrefix; }
                    @Optional public boolean isVerbose() { return verbose; }
                    @Optional public boolean isPrintSummary() { return printSummary; }
    @Input          @Optional public boolean isFailOnError() { return failOnError; }
    @Input          @Optional public String getOriginalPrefix() { return originalPrefix; }
    @Input          @Optional public String getModifiedPrefix() { return modifiedPrefix; }
                              public void setBase(File base) { this.base = base; }
                              public void setPatches(File patches) { this.patches = patches; }
                              public void setOutput(File output) { this.output = output; }
                              public void setRejects(File rejects) { this.rejects = rejects; }
                              public void setOutputFormat(ArchiveFormat outputFormat) { this.outputFormat = outputFormat; }
                              public void setRejectsFormat(ArchiveFormat rejectsFormat) { this.rejectsFormat = rejectsFormat; }
                              public void setMinFuzzQuality(float minFuzzQuality) { this.minFuzzQuality = minFuzzQuality; }
                              public void setMaxFuzzOffset(int maxFuzzOffset) { this.maxFuzzOffset = maxFuzzOffset; }
                              public void setPatchMode(PatchMode patchMode) { this.patchMode = patchMode; }
                              public void setPatchesPrefix(String patchesPrefix) { this.patchesPrefix = patchesPrefix; }
                              public void setVerbose(boolean verbose) { this.verbose = verbose; }
                              public void setPrintSummary(boolean printSummary) { this.printSummary = printSummary; }
                              public void setFailOnError(boolean failOnError) { this.failOnError = failOnError; }
                              public void setOriginalPrefix(String originalPrefix) { this.originalPrefix = originalPrefix; }
                              public void setModifiedPrefix(String modifiedPrefix) { this.modifiedPrefix = modifiedPrefix; }
    //@formatter:on
}

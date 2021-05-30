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

package net.minecraftforge.gradle.patcher.tasks;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.DiffOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.archiver.ArchiveFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.*;

import java.io.File;
import java.nio.file.Path;

public class GeneratePatches extends DefaultTask {

    private File base;
    private File modified;
    private File output;
    private ArchiveFormat outputFormat;
    private boolean autoHeader;
    private int contextLines = -1;
    private boolean verbose = false;
    private boolean printSummary = false;

    private String originalPrefix = "a/";
    private String modifiedPrefix = "b/";
    private String lineEnding = System.lineSeparator();

    @TaskAction
    public void doTask() throws Exception {
        Path outputPath = getOutput().toPath();
        ArchiveFormat outputFormat = getOutputFormat();
        if (outputFormat == null) {
            outputFormat = ArchiveFormat.findFormat(outputPath.getFileName());
        }
        getProject().getLogger().info("Base:" + getBase().toString());
        getProject().getLogger().info("Modified:" + getModified().toString());

        DiffOperation.Builder builder = DiffOperation.builder()
                .logTo(new LoggingOutputStream(getLogger(), LogLevel.LIFECYCLE))
                .aPath(getBase().toPath())
                .bPath(getModified().toPath())
                .outputPath(outputPath, outputFormat)
                .autoHeader(isAutoHeader())
                .verbose(isVerbose())
                .summary(isPrintSummary())
                .aPrefix(originalPrefix)
                .bPrefix(modifiedPrefix)
                .lineEnding(lineEnding)
                ;

        int context = getContextLines();
        if (context != -1) {
            builder.context(context);
        }

        CliOperation.Result<DiffOperation.DiffSummary> result = builder.build().operate();

        int exit = result.exit;
        if (exit != 0 && exit != 1) {
            throw new RuntimeException("DiffPatch failed with exit code: " + exit);
        }
    }

    //@formatter:off
    @InputFile                 public File getBase() { return base; }
    @InputFile                 public File getModified() { return modified; }
    @OutputDirectory           public File getOutput() { return output; }
    @Input           @Optional public ArchiveFormat getOutputFormat() { return outputFormat; }
    @Input                     public boolean isAutoHeader() { return autoHeader; }
    @Input                     public int getContextLines() { return contextLines; }
    @Input                     public boolean isVerbose() { return verbose; }
    @Internal                  public boolean isPrintSummary() { return printSummary; }
    @Input           @Optional public String getOriginalPrefix() { return originalPrefix; }
    @Input           @Optional public String getModifiedPrefix() { return modifiedPrefix; }
    @Input                     public String getLineEnding() { return lineEnding; }
                               public void setBase(File base) { this.base = base; }
                               public void setModified(File modified) { this.modified = modified; }
                               public void setOutput(File patches) { this.output = patches; }
                               public void setOutputFormat(ArchiveFormat format) { this.outputFormat = format; }
                               public void setAutoHeader(boolean autoHeader) { this.autoHeader = autoHeader; }
                               public void setContextLines(int lines) { this.contextLines = lines; }
                               public void setVerbose(boolean verbose) { this.verbose = verbose; }
                               public void setPrintSummary(boolean printSummary) { this.printSummary = printSummary; }
                               public void setOriginalPrefix(String originalPrefix) { this.originalPrefix = originalPrefix; }
                               public void setModifiedPrefix(String modifiedPrefix) { this.modifiedPrefix = modifiedPrefix; }
                               public void setLineEnding(String value) { this.lineEnding = value; }
    //@formatter:on
}

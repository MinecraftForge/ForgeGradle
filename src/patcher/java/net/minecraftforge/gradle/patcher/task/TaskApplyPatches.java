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

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.cloudbees.diff.PatchException;

import net.minecraftforge.gradle.common.diff.ContextualPatch;
import net.minecraftforge.gradle.common.diff.HunkReport;
import net.minecraftforge.gradle.common.diff.PatchFile;
import net.minecraftforge.gradle.common.diff.ZipContext;
import net.minecraftforge.gradle.common.diff.ContextualPatch.PatchReport;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipFile;

public class TaskApplyPatches extends DefaultTask {

    private File clean;
    private File patches;
    private File output = getProject().file("build/" + getName() + "/output.zip");
    private File rejects = getProject().file("build/" + getName() + "/rejects.zip");
    private int maxFuzz = 0;
    private boolean canonicalizeAccess = true;
    private boolean canonicalizeWhitespace = true;
    private boolean failOnErrors = true;
    private String originalPrefix;
    private String modifiedPrefix;

    @TaskAction
    public void applyPatches() {
        try (ZipFile zip = new ZipFile(getClean())) {
            ZipContext context = new ZipContext(zip);

            if (getPatches() == null) {
                context.save(getOutput());
                return;
            }

            boolean all_success = Files.walk(getPatches().toPath())
            .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".patch"))
            .map(p -> {
                boolean success = true;
                ContextualPatch patch = ContextualPatch.create(PatchFile.from(p.toFile()), context, getOriginalPrefix(), getModifiedPrefix());
                patch.setCanonialization(getCanonicalizeAccess(), getCanonicalizeWhitespace());
                patch.setMaxFuzz(getMaxFuzz());
                String name = p.toFile().getAbsolutePath().substring(getPatches().getAbsolutePath().length() + 1);

                try {
                    getLogger().info("Apply Patch: " + name);
                    List<PatchReport> result = patch.patch(false);
                    for (int x = 0; x < result.size(); x++) {
                        PatchReport report = result.get(x);
                        if (!report.getStatus().isSuccess()) {
                            getLogger().error("  Apply Patch: " + name);
                            success = false;
                            for (int y = 0; y < report.hunkReports().size(); y++) {
                                HunkReport hunk = report.hunkReports().get(y);
                                if (hunk.hasFailed()) {
                                    if (hunk.failure == null) {
                                        getLogger().error("    Hunk #" + hunk.hunkID + " Failed @" + hunk.index + " Fuzz: " + hunk.fuzz);
                                    } else {
                                        getLogger().error("    Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (PatchException e) {
                    getLogger().error("  Apply Patch: " + name);
                    getLogger().error("    " + e.getMessage());
                } catch (IOException e) {
                    getLogger().error("  Apply Patch: " + name);
                    throw new RuntimeException(e);
                }
                return success;
            }).reduce(true, (a,b) -> a && b);

            if (all_success || !failOnErrors) {
                context.save(getOutput());
                saveRejects(context);
            } else {
                saveRejects(context);
                throw new RuntimeException("Failed to apply patches. See log for details.");
            }
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }
    }

    private void saveRejects(ZipContext context) throws IOException {
        File rejectsFile = getRejects();
        if (!rejectsFile.getName().endsWith(".zip") && !rejectsFile.getName().endsWith(".jar") && !rejectsFile.isFile()) {
            File tmp = new File(getTemporaryDir(), "rejects_tmp.zip");
            context.saveRejects(tmp);
            getProject().copy(spec -> {
                spec.from(getProject().zipTree(tmp));
                spec.into(rejectsFile);
            });
        } else {
            context.saveRejects(rejectsFile);
        }
    }

    @InputFile
    public File getClean() {
        return clean;
    }

    @Optional
    @InputDirectory
    public File getPatches() {
        return patches;
    }

    @Input
    public int getMaxFuzz() {
        return maxFuzz;
    }

    @Input
    public boolean getCanonicalizeWhitespace() {
        return canonicalizeWhitespace;
    }

    @Input
    public boolean getCanonicalizeAccess() {
        return canonicalizeAccess;
    }

    @Input
    @Optional
    public String getOriginalPrefix() {
    	return this.originalPrefix;
    }

    @Input
    @Optional
    public String getModifiedPrefix() {
    	return this.modifiedPrefix;
    }

    public boolean getFailOnErrors() {
        return failOnErrors;
    }
    public void setFailOnErrors(boolean value) {
        this.failOnErrors = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    @OutputFile
    public File getRejects() {
        return rejects;
    }

    public void setClean(File clean) {
        this.clean = clean;
    }

    public void setPatches(File value) {
        patches = value;
    }

    public void setMaxFuzz(int value) {
        maxFuzz = value;
    }

    public void setCanonicalizeWhitespace(boolean value) {
        canonicalizeWhitespace = value;
    }

    public void setCanonicalizeAccess(boolean value) {
        canonicalizeAccess = value;
    }

    public void setOriginalPrefix(String value) {
    	this.originalPrefix = value;
    }

    public void setModifiedPrefix(String value) {
    	this.modifiedPrefix = value;
    }

    public void setOutput(File value) {
        output = value;
    }

    public void setRejects(File rejects) {
        this.rejects = rejects;
    }
}

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

package net.minecraftforge.gradle.mcp.function;

import com.cloudbees.diff.PatchException;
import net.minecraftforge.gradle.common.diff.ContextualPatch;
import net.minecraftforge.gradle.common.diff.ContextualPatch.PatchReport;
import net.minecraftforge.gradle.common.diff.HunkReport;
import net.minecraftforge.gradle.common.diff.PatchFile;
import net.minecraftforge.gradle.common.diff.ZipContext;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class PatchFunction implements MCPFunction {

    private String path;
    private Map<String, String> patches;

    @Override
    public  void loadData(Map<String, String> data) {
        path = data.get("patches");
    }

    @Override
    public  void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        patches = zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(path) && e.getName().endsWith(".patch"))
        .collect(Collectors.toMap(e -> e.getName().substring(path.length()), e -> {
            try {
                return IOUtils.toString(zip.getInputStream(e));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
    }


    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = (File)environment.getArguments().get("input");
        File output = environment.getFile("output.jar");

        File hashFile = environment.getFile("lastinput.sha1");
        HashStore hashStore = new HashStore(environment.project).load(hashFile);
        hashStore.add(input);
        patches.forEach((path,data) -> hashStore.add(path, data));

        if (hashStore.isSame() && output.exists()) return output;

        try (ZipFile zip = new ZipFile(input)) {
            ZipContext context = new ZipContext(zip);

            boolean success = true;
            for (Entry<String, String> entry : patches.entrySet()) {
                ContextualPatch patch = ContextualPatch.create(PatchFile.from(entry.getValue()), context);
                patch.setCanonialization(true, false); //We are applying MCP patches, Which can have access transformers, but not refactors.
                patch.setMaxFuzz(0); //They should also never fuzz.

                try {
                    environment.logger.info("Apply Patch: " + entry.getKey());
                    List<PatchReport> result = patch.patch(false);
                    for (int x = 0; x < result.size(); x++) {
                        PatchReport report = result.get(x);
                        if (!report.getStatus().isSuccess()) {
                            success = false;
                            for (int y = 0; y < report.hunkReports().size(); y++) {
                                HunkReport hunk = report.hunkReports().get(y);
                                if (hunk.hasFailed()) {
                                    if (hunk.failure == null) {
                                        environment.logger.error("  Hunk #" + hunk.hunkID + " Failed @" + hunk.index + " Fuzz: " + hunk.fuzz);
                                    } else {
                                        environment.logger.error("  Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.getMessage());
                                    }

                                }
                            }
                        }
                    }
                } catch (PatchException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (success) {
                context.save(output);
                hashStore.save(hashFile);
            }
        }

        return output;
    }

    @Override
    public  void cleanup(MCPEnvironment environment) {
        this.patches.clear();
    }

}

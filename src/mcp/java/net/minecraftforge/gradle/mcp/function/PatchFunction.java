/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import codechicken.diffpatch.cli.CliOperation;
import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.LoggingOutputStream;
import codechicken.diffpatch.util.PatchMode;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.IOUtils;
import org.gradle.api.logging.LogLevel;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class PatchFunction implements MCPFunction {

    private String path;

    @Override
    public void loadData(Map<String, String> data) {
        path = data.get("patches");
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = (File) environment.getArguments().get("input");
        File output = environment.getFile("output.jar");
        File rejects = environment.getFile("rejects.zip");

        File hashFile = environment.getFile("lastinput.sha1");
        HashStore hashStore = new HashStore(environment.project).load(hashFile);
        hashStore.add(input);

        //Read patches into HashStore
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(environment.getConfigZip()))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().startsWith(path)) {
                    hashStore.add(e.getName().substring(path.length()), IOUtils.toByteArray(zin));
                }
            }
        }

        if (hashStore.isSame() && output.exists()) {
            return output;
        }

        CliOperation.Result<PatchOperation.PatchesSummary> result = PatchOperation.builder()
                .logTo(new LoggingOutputStream(environment.logger, LogLevel.LIFECYCLE))
                .basePath(input.toPath())
                .patchesPath(environment.getConfigZip().toPath())
                .patchesPrefix(path)
                .outputPath(output.toPath())
                .verbose(false)
                .mode(PatchMode.OFFSET)
                .rejectsPath(rejects.toPath())
                .build()
                .operate();

        boolean success = result.exit == 0;
        if (!success) {
            environment.logger.error("Rejects saved to: {}", rejects);
            throw new RuntimeException("Patch failure.");
        } else {
            hashStore.save(hashFile);
        }
        return output;
    }
}

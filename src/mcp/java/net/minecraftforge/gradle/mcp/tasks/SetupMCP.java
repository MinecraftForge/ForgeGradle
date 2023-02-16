/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.tasks;

import net.minecraftforge.gradle.common.config.MCPConfigV2;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;

import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public abstract class SetupMCP extends DefaultTask {
    public SetupMCP() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.zip")));

        this.getOutputs().upToDateWhen(task -> {
            HashStore cache = new HashStore(getProject());
            try {
                cache.load(getProject().file("build/" + getName() + "/inputcache.sha1"));
                cache.add("configFile", getConfig().get().getAsFile());
                getPreDecompile().get().forEach((key, func) -> func.addInputs(cache, key + "."));
                cache.save();
                return cache.isSame() && getOutput().get().getAsFile().exists();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    @InputFile
    public abstract RegularFileProperty getConfig();

    @Input
    public abstract Property<String> getPipeline();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract MapProperty<String, MCPFunction> getPreDecompile();

    @TaskAction
    public void setupMCP() throws Exception {
        File config = getConfig().get().getAsFile();
        File output = getOutput().get().getAsFile();

        MCPConfigV2 mcpconfig = MCPConfigV2.getFromArchive(config);
        MCPRuntime runtime = new MCPRuntime(getProject(), config, mcpconfig, getPipeline().get(), getProject().file("build/mcp/"), getPreDecompile().get());
        File out = runtime.execute(getLogger());
        if (FileUtils.contentEquals(out, output)) return;
        Utils.delete(output);
        FileUtils.copyFile(out, output);
    }
}

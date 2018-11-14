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

package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SetupMCPTask extends DefaultTask {
    private File config;
    private String pipeline;

    private Map<String, MCPFunction> extrasPre = new LinkedHashMap<>();

    private File output = getProject().file("build/" + getName() + "/output.zip");

    public SetupMCPTask() {
        this.getOutputs().upToDateWhen(task -> {
            HashStore cache = new HashStore(getProject());
            try {
                cache.load(getProject().file("build/" + getName() + "/inputcache.sha1"));
                cache.add("configFile", config);
                extrasPre.forEach((key, func) -> func.addInputs(cache, key + "."));
                cache.save();
                return cache.isSame() && getOutput().exists();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        });
    }

    @InputFile
    public File getConfig() {
        return config;
    }
    public void setConfig(File value) {
        this.config = value;
    }

    @Input
    public String getPipeline() {
        return this.pipeline;
    }
    public void setPipeline(String value) {
        this.pipeline = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @TaskAction
    public void setupMCP() throws Exception {
        byte[] config_data = Utils.getZipData(config, "config.json");
        int spec = Config.getSpec(config_data);
        if (spec != 1)
            throw new IllegalStateException("Invalid MCP Config: " + config + " Unknown spec: " + spec);

        MCPRuntime runtime = new MCPRuntime(getProject(), config, MCPConfigV1.get(config_data), getPipeline(), getProject().file("build/mcp/"), extrasPre);
        File out = runtime.execute(getLogger());
        if (FileUtils.contentEquals(out, output)) return;
        Utils.delete(output);
        FileUtils.copyFile(out, output);
    }

    public void addPreDecompile(String name, MCPFunction function) {
        this.extrasPre.put(name, function);
    }
}

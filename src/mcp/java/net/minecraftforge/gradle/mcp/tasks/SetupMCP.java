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
                cache.add("configFile", getOutput().get().getAsFile());
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

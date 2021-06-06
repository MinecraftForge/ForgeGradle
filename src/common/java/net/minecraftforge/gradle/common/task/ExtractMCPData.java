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

package net.minecraftforge.gradle.common.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.config.MCPConfigV2;

public class ExtractMCPData extends DefaultTask {
    private String key = "mappings";
    private Supplier<File> configSupplier;
    private File config;
    private boolean allowEmpty = false;
    private File output = getProject().file("build/" + getName() + "/output.srg");

    @TaskAction
    public void run() throws IOException {
        MCPConfigV1 cfg = MCPConfigV2.getFromArchive(getConfig());

        try (ZipFile zip = new ZipFile(getConfig())) {
            String path = cfg.getData(key.split("/"));
            if (path == null && "statics".equals(key))
                path = "config/static_methods.txt";

            if (path == null) {
                error("Could not find data entry for '" + key + "'");
                return;
            }

            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                error("Invalid config zip, Missing path '" + path + "'");
                return;
            }

            try (OutputStream out = new FileOutputStream(getOutput())) {
                IOUtils.copy(zip.getInputStream(entry), out);
            }
        }
    }

    private void error(String message) throws IOException {
        if (!isAllowEmpty())
            throw new IllegalStateException(message);

        if (getOutput().exists())
            getOutput().delete();

        getOutput().createNewFile();
    }

    @InputFile
    public File getConfig() {
        if (config == null && configSupplier != null)
            config = configSupplier.get();

        return config;
    }
    public void setConfig(File value) {
        this.config = value;
        this.configSupplier = null;
    }
    public void setConfig(Supplier<File> valueSupplier)
    {
        this.configSupplier = valueSupplier;
        this.config = null;
    }

    @Input
    public String getKey() {
        return this.key;
    }
    public void setKey(String value) {
        this.key = value;
    }

    @Input
    public boolean isAllowEmpty() {
        return this.allowEmpty;
    }
    public void setAllowEmpty(boolean value) {
        this.allowEmpty = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

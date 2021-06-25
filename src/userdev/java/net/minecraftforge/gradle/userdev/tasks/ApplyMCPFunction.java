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

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.config.MCPConfigV2;
import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class ApplyMCPFunction extends JarExec {
    private final Map<String, Object> replacements = new HashMap<>();

    public ApplyMCPFunction() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
    }

    @TaskAction
    public void apply() throws IOException {
        File mcp = getMCP().get().getAsFile();
        MCPConfigV1 config = MCPConfigV2.getFromArchive(mcp);
        MCPConfigV1.Function function = config.getFunction(getFunctionName().get());

        getTool().set(function.getVersion());
        getArgs().set(function.getArgs());

        try (ZipFile zip = new ZipFile(mcp)) {
            function.getArgs().forEach(arg -> {
                // A token must start with {, end with }, and be at least 3 large ("{x}")
                if (arg.length() < 2 || !arg.startsWith("{") || !arg.endsWith("}")) return;
                String argName = arg.substring(1, arg.length() - 1);

                switch (argName) {
                    case "input":
                        replacements.put(arg, getInput().get().getAsFile().getAbsolutePath());
                        break;
                    case "output":
                        replacements.put(arg, getOutput().get().getAsFile().getAbsolutePath());
                        break;
                    case "log":
                        replacements.put(arg, getOutput().get().getAsFile().getAbsolutePath() + ".log");
                        break;
                    default:
                        Object referencedData = config.getData().get(argName);
                        if (referencedData instanceof String) {
                            ZipEntry entry = zip.getEntry((String) referencedData);
                            if (entry == null) return;
                            String entryName = entry.getName();

                            try {
                                File data = makeFile(entry.getName());
                                if (entry.isDirectory()) {
                                    Utils.extractDirectory(this::makeFile, zip, entryName);
                                } else {
                                    Utils.extractFile(zip, entry, data);
                                }
                                replacements.put(arg, data.getAbsolutePath());
                            } catch (IOException e) {
                                getLogger().debug("Exception while extracting referenced data for token {} in task {}", arg, getName(), e);
                            }
                        }
                        break;
                }
            });
        }

        super.apply();
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, replacements, null);
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getMCP();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<String> getFunctionName();

    private File makeFile(String name) {
        return new File(getOutput().get().getAsFile().getParent(), name);
    }
}

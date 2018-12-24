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

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ApplyMCPFunction extends JarExec {
    private static final Pattern REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)\\}$");

    private File input;
    private File output;
    private File mcp;

    private String functionName;
    private Map<String, String> replacements = new HashMap<>();

    public ApplyMCPFunction() {}

    @TaskAction
    public void apply() throws IOException {
        byte[] cfg_data = Utils.getZipData(getMCP(), "config.json");
        int spec = Config.getSpec(cfg_data);
        if (spec != 1)
            throw new IllegalStateException("Could not load MCP config, Unknown Spec: " + spec + " File: " + getMCP());
        MCPConfigV1 config = MCPConfigV1.get(cfg_data);

        MCPConfigV1.Function function = config.getFunction(functionName);

        tool = function.getVersion();
        args = function.getArgs().toArray(new String[0]);

        try (ZipFile zip = new ZipFile(getMCP())) {
            function.getArgs().forEach(arg -> {
                Matcher matcher = REPLACE_PATTERN.matcher(arg);
                String argName = matcher.find() ? matcher.group(1) : null;
                if (argName == null) return;

                if (argName.equals("input")) {
                    replacements.put(arg, getInput().getAbsolutePath());
                }
                else if (argName.equals("output")) {
                    replacements.put(arg, getOutput().getAbsolutePath());
                }
                else if (argName.equals("log")) {
                    replacements.put(arg, getOutput().getAbsolutePath() + ".log");
                }
                else {
                    Object referencedData = config.getData().get(argName);
                    if (referencedData instanceof String) {
                        ZipEntry entry = zip.getEntry((String)referencedData);
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
                        } catch (IOException e) {}
                    }
                }
            });
        }

        super.apply();
    }

    @Override
    protected List<String> filterArgs() {
        return Arrays.stream(getArgs()).map(arg -> replacements.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @InputFile
    public File getMCP() {
        return mcp;
    }
    public void setMCP(File value) {
        this.mcp = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            setOutput(getProject().file("build/" + getName() + "/output.jar"));
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }

    public void setFunctionName(String name) {
        functionName = name;
    }

    private File makeFile(String name) {
        return new File(getOutput().getParent(), name);
    }
}

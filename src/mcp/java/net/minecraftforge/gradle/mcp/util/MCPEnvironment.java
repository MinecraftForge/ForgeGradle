/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.srgutils.MinecraftVersion;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import java.io.File;
import java.util.Map;

public class MCPEnvironment {

    private final MCPRuntime runtime;
    public final Project project;
    public final String side;
    public Logger logger;
    private final MinecraftVersion mcVersion;
    private final JavaLanguageVersion javaVersion;

    public MCPEnvironment(MCPRuntime runtime, String mcVersion, int javaVersion, String side) {
        this.runtime = runtime;
        this.project = runtime.project;
        this.side = side;
        this.mcVersion = MinecraftVersion.from(mcVersion);
        this.javaVersion = JavaLanguageVersion.of(javaVersion);
    }

    public Map<String, Object> getArguments() {
        return runtime.currentStep.arguments;
    }

    public File getWorkingDir() {
        return runtime.currentStep.workingDirectory;
    }

    public File getConfigZip() {
        return runtime.zipFile;
    }

    public File getFile(String name) {
        File file = new File(name);
        if (file.getAbsolutePath().equals(name)) { // If this is already an absolute path, don't mess with it
            return file;
        } else if (name.startsWith("/")) {
            return new File(runtime.mcpDirectory, name);
        } else {
            return new File(getWorkingDir(), name);
        }
    }

    public File getStepOutput(String name) {
        MCPRuntime.Step step = runtime.steps.get(name);
        if (step == null) {
            throw new IllegalArgumentException("Could not find a step named " + name);
        }
        if (step.output == null) {
            throw new IllegalArgumentException("Attempted to get the output of an unexecuted step: " + name);
        }
        return step.output;
    }

    public MinecraftVersion getMinecraftVersion() {
        return this.mcVersion;
    }

    /**
     * @return The Java version used to run the MCP steps (decompilation, etc.)
     */
    public JavaLanguageVersion getJavaVersion() {
        return javaVersion;
    }
}

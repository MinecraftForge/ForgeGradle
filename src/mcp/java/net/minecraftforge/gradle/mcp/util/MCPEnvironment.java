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

package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.srgutils.MinecraftVersion;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Map;

public class MCPEnvironment {

    private final MCPRuntime runtime;
    public final Project project;
    public final String side;
    public Logger logger;
    private final MinecraftVersion mcVersion;

    public MCPEnvironment(MCPRuntime runtime, String mcVersion, String side) {
        this.runtime = runtime;
        this.project = runtime.project;
        this.side = side;
        this.mcVersion = MinecraftVersion.from(mcVersion);
    }

    public Map<String, Object> getArguments() {
        return runtime.currentStep.arguments;
    }

    public File getWorkingDir() {
        return runtime.currentStep.workingDirectory;
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

    public File getStepOutput(Class<? extends MCPFunction> type) {
        for (MCPRuntime.Step step : runtime.steps.values()) {
            if (step.isOfType(type)) {
                return step.output;
            }
        }
        throw new IllegalArgumentException("Could not find a step of type " + type.getName());
    }

    public MinecraftVersion getMinecraftVersion() {
        return this.mcVersion;
    }

}

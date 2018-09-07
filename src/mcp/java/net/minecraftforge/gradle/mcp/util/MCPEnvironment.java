package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.gradle.mcp.function.MCPFunction;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Map;

public class MCPEnvironment {

    private final MCPRuntime runtime;
    public final Project project;
    public final String mcVersion;
    public Logger logger;

    public MCPEnvironment(MCPRuntime runtime, String mcVersion) {
        this.runtime = runtime;
        this.project = runtime.project;
        this.mcVersion = mcVersion;
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

}

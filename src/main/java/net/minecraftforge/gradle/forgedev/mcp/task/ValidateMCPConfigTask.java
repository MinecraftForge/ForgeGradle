package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.MCPPlugin;
import net.minecraftforge.gradle.forgedev.mcp.function.ExecuteFunction;
import net.minecraftforge.gradle.forgedev.mcp.function.MCPFunction;
import net.minecraftforge.gradle.forgedev.mcp.util.MCPConfig;
import net.minecraftforge.gradle.forgedev.mcp.util.RawMCPConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class ValidateMCPConfigTask extends DefaultTask {

    @Input
    public RawMCPConfig unprocessed;

    public final MCPConfig processed = new MCPConfig();

    private final Map<String, MCPFunction> visitedFunctions = new HashMap<>();

    @TaskAction
    public void validate() throws Exception {
        processed.mcVersion = unprocessed.mcVersion;

        processSteps(unprocessed.pipeline.sharedSteps, processed.pipeline::addShared);
        processSteps(unprocessed.pipeline.srcSteps, processed.pipeline::addSrc);

        processed.libraries.client.addAll(unprocessed.libraries.client);
        processed.libraries.server.addAll(unprocessed.libraries.server);
        processed.libraries.joined.addAll(unprocessed.libraries.joined);
    }

    private void processSteps(List<RawMCPConfig.Pipeline.Step> steps, StepAdder adder) throws Exception {
        for (RawMCPConfig.Pipeline.Step step : steps) {
            MCPFunction function = visitedFunctions.computeIfAbsent(step.type, this::findFunction);
            if (function == null) {
                throw new IllegalStateException("Expected a function of type '" + step.type + "' to be available, but it's not!");
            }
            function.loadData(unprocessed.data);
            adder.addStep(step.type, function, step.arguments);
        }
    }

    private MCPFunction findFunction(String type) {
        RawMCPConfig.Function function = unprocessed.functions.get(type);
        if (function != null) {
            CompletableFuture<File> jar = new CompletableFuture<>();
            processed.dependencies.put(function.version, jar); // Pull the jar from maven later
            return new ExecuteFunction(jar, function.jvmArgs, function.runArgs, function.envVars);
        }
        return MCPPlugin.createBuiltInFunction(getProject(), type);
    }

    private interface StepAdder {

        void addStep(String type, MCPFunction function, Map<String, String> arguments);

    }

}

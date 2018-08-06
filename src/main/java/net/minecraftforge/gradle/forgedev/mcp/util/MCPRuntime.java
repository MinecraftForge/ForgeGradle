package net.minecraftforge.gradle.forgedev.mcp.util;

import net.minecraftforge.gradle.forgedev.mcp.function.MCPFunction;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MCPRuntime {

    private static final Pattern OUTPUT_REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)Output\\}$");

    final Project project;
    final MCPEnvironment environment;
    final File mcpDirectory;

    final Map<String, Step> steps = new LinkedHashMap<>();
    Step currentStep;

    public MCPRuntime(Project project, MCPConfig config, boolean generateSrc) {
        this.project = project;
        this.environment = new MCPEnvironment(this, config.mcVersion);
        this.mcpDirectory = project.file("mcp/");

        initSteps(config.pipeline.sharedSteps);
        if (generateSrc) {
            initSteps(config.pipeline.srcSteps);
        }
    }

    private void initSteps(List<MCPConfig.Pipeline.Step> steps) {
        for (MCPConfig.Pipeline.Step step : steps) {
            File workingDir = new File(this.mcpDirectory, step.name);
            this.steps.put(step.name, new Step(step.name, step.function, step.arguments, workingDir));
        }
    }

    public void execute(Logger logger) throws Exception {
        logger.info("Setting up MCP environment!");
        for (Step step : steps.values()) {
            logger.info(" > Running '" + step.name + "'");
            currentStep = step;
            step.arguments.replaceAll((key, value) -> applyStepOutputSubstitutions(value));
            step.execute();
        }
        logger.info("MCP environment setup is complete!");
    }

    private String applyStepOutputSubstitutions(String value) {
        Matcher matcher = OUTPUT_REPLACE_PATTERN.matcher(value);
        if (!matcher.find()) return value; // Not a replaceable string

        String stepName = matcher.group(1);
        if (stepName != null) {
            return environment.getStepOutput(stepName).getAbsolutePath();
        }
        throw new IllegalStateException("The string '" + value + "' did not return a valid substitution match!");
    }

    class Step {

        private final String name;
        private final MCPFunction function;
        final Map<String, String> arguments;
        final File workingDirectory;
        File output;

        private Step(String name, MCPFunction function, Map<String, String> arguments, File workingDirectory) {
            this.name = name;
            this.function = function;
            this.arguments = arguments;
            this.workingDirectory = workingDirectory;
        }

        private void execute() throws Exception {
            output = function.execute(environment);
        }

        boolean isOfType(Class<? extends MCPFunction> type) {
            return type.isAssignableFrom(function.getClass());
        }

    }

}

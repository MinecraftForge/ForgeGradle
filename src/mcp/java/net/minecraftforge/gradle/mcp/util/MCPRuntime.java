package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionOverlay;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class MCPRuntime {

    private static final Pattern OUTPUT_REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)Output\\}$");

    final Project project;
    final MCPEnvironment environment;
    final File mcpDirectory;

    private final File zipFile;

    final Map<String, Step> steps = new LinkedHashMap<>();
    Step currentStep;

    public MCPRuntime(Project project, MCPConfig config, boolean generateSrc) {
        this.project = project;
        this.environment = new MCPEnvironment(this, config.mcVersion);
        this.mcpDirectory = project.file("build/mcp/");

        this.zipFile = config.zipFile;

        initSteps(config.pipeline.sharedSteps);
        if (generateSrc) {
            initSteps(config.pipeline.srcSteps);
        }
    }

    private void initSteps(List<MCPConfig.Pipeline.Step> steps) {
        for (MCPConfig.Pipeline.Step step : steps) {
            File workingDir = new File(this.mcpDirectory, step.name);
            this.steps.put(step.name, new Step(step.name, step.function, step.overlay, step.arguments, workingDir));
        }
    }

    public File execute(Logger logger) throws Exception {
        environment.logger = logger;

        logger.info("Setting up MCP environment!");

        logger.info("Initializing steps!");
        ZipFile zip = new ZipFile(zipFile);
        for (Step step : steps.values()) {
            logger.info(" > Initializing '" + step.name + "'");
            currentStep = step;
            step.initialize(zip);
        }
        zip.close();

        File ret = null;
        logger.info("Executing steps!");
        for (Step step : steps.values()) {
            logger.info(" > Running '" + step.name + "'");
            currentStep = step;
            step.arguments.replaceAll((key, value) -> applyStepOutputSubstitutions(value));
            ret = step.execute();
        }

        logger.info("MCP environment setup is complete!");
        return ret;
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
        private final MCPFunctionOverlay overlay;
        final Map<String, String> arguments;
        final File workingDirectory;
        File output;

        private Step(String name, MCPFunction function, MCPFunctionOverlay overlay,
                     Map<String, String> arguments, File workingDirectory) {
            this.name = name;
            this.function = function;
            this.overlay = overlay;
            this.arguments = arguments;
            this.workingDirectory = workingDirectory;
        }

        private void initialize(ZipFile zip) throws Exception {
            function.initialize(environment, zip);
            if (overlay != null) overlay.initialize(environment, zip);
        }

        private File execute() throws Exception {
            try {
                output = function.execute(environment);
            } finally {
                function.cleanup(environment);
            }
            if (overlay != null) {
                try {
                    overlay.onExecuted(environment);
                } finally {
                    overlay.cleanup(environment);
                }
            }
            return output;
        }

        boolean isOfType(Class<? extends MCPFunction> type) {
            return type.isAssignableFrom(function.getClass());
        }

    }

}

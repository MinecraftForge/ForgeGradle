package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionOverlay;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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

    public MCPRuntime(Project project, MCPConfig config, boolean generateSrc, Map<String, MCPFunction> extraPres) {
        this.project = project;
        this.environment = new MCPEnvironment(this, config.mcVersion);
        this.mcpDirectory = project.file("build/mcp/");

        this.zipFile = config.zipFile;

        initSteps(config.pipeline.sharedSteps);
        if (!extraPres.isEmpty()) {
            String input = config.pipeline.srcSteps.get(0).arguments.get("input"); //Decompile's input
            String lastName = null;
            for (Entry<String, MCPFunction> entry : extraPres.entrySet()) {
                String name = entry.getKey();
                Map<String, String> args = new HashMap<>();
                args.put("input", input);
                this.steps.put(name, new Step(name, entry.getValue(), null, args, new File(this.mcpDirectory, name)));
                input = "{" + name +"Output}";
                lastName = name;
            }
            config.pipeline.srcSteps.get(0).arguments.put("input", "{" + lastName + "Output}");
        }
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

        logger.lifecycle("Setting up MCP environment!");

        logger.lifecycle("Initializing steps!");
        ZipFile zip = new ZipFile(zipFile);
        for (Step step : steps.values()) {
            logger.info(" > Initializing '" + step.name + "'");
            currentStep = step;
            step.initialize(zip);
        }
        zip.close();

        File ret = null;
        logger.lifecycle("Executing steps!");
        for (Step step : steps.values()) {
            logger.lifecycle(" > Running '" + step.name + "'");
            currentStep = step;
            step.arguments.replaceAll((key, value) -> applyStepOutputSubstitutions(value));
            ret = step.execute();
        }

        logger.lifecycle("MCP environment setup is complete!");
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

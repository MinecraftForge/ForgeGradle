package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.gradle.mcp.function.MCPFunction;
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

    public MCPRuntime(Project project, MCPConfig config, File mcpDirectory, boolean generateSrc, Map<String, MCPFunction> extraPres) {
        this.project = project;
        this.environment = new MCPEnvironment(this, config.getMCVersion());
        this.mcpDirectory = mcpDirectory;

        this.zipFile = config.getConfigZip();

        initSteps(config.getSharedSteps());
        if (!extraPres.isEmpty()) {
            String input = config.getSrcSteps().get(0).getArguments().get("input"); //Decompile's input
            String lastName = null;
            for (Entry<String, MCPFunction> entry : extraPres.entrySet()) {
                String name = entry.getKey();
                Map<String, String> args = new HashMap<>();
                args.put("input", input);
                this.steps.put(name, new Step(name, entry.getValue(), args, new File(this.mcpDirectory, name)));
                input = "{" + name +"Output}";
                lastName = name;
            }
            config.getSrcSteps().get(0).getArguments().put("input", "{" + lastName + "Output}");
        }
        if (generateSrc) {
            initSteps(config.getSrcSteps());
        }
    }

    private void initSteps(List<MCPConfig.Step> steps) {
        for (MCPConfig.Step step : steps) {
            File workingDir = new File(this.mcpDirectory, step.getName());
            this.steps.put(step.getName(), new Step(step.getName(), step.getFunction(), step.getArguments(), workingDir));
        }
    }

    public File execute(Logger logger) throws Exception {
        environment.logger = logger;

        logger.lifecycle("Setting up MCP environment");

        logger.lifecycle("Initializing steps");
        ZipFile zip = new ZipFile(zipFile);
        for (Step step : steps.values()) {
            logger.info(" > Initializing '" + step.name + "'");
            currentStep = step;
            step.initialize(zip);
        }
        zip.close();

        File ret = null;
        logger.lifecycle("Executing steps");
        for (Step step : steps.values()) {
            logger.lifecycle(" > Running '" + step.name + "'");
            currentStep = step;
            step.arguments.replaceAll((key, value) -> value instanceof String ? applyStepOutputSubstitutions((String)value) : value);
            ret = step.execute();
        }

        logger.lifecycle("MCP environment setup is complete");
        return ret;
    }

    private Object applyStepOutputSubstitutions(String value) {
        Matcher matcher = OUTPUT_REPLACE_PATTERN.matcher(value);
        if (!matcher.find()) return value; // Not a replaceable string

        String stepName = matcher.group(1);
        if (stepName != null) {
            return environment.getStepOutput(stepName);
        }
        throw new IllegalStateException("The string '" + value + "' did not return a valid substitution match!");
    }

    class Step {

        private final String name;
        private final MCPFunction function;
        final Map<String, Object> arguments;
        final File workingDirectory;
        File output;

        private Step(String name, MCPFunction function, Map<String, String> arguments, File workingDirectory) {
            this.name = name;
            this.function = function;
            this.arguments = new HashMap<>(arguments);
            this.workingDirectory = workingDirectory;
        }

        private void initialize(ZipFile zip) throws Exception {
            function.initialize(environment, zip);
        }

        private File execute() throws Exception {
            try {
                output = function.execute(environment);
            } finally {
                function.cleanup(environment);
            }
            return output;
        }

        boolean isOfType(Class<? extends MCPFunction> type) {
            return type.isAssignableFrom(function.getClass());
        }

    }
}

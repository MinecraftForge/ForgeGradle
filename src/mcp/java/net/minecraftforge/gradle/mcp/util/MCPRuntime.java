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

import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.function.ExecuteFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class MCPRuntime {

    private static final Pattern OUTPUT_REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)Output\\}$");

    final Project project;
    final MCPEnvironment environment;
    final File mcpDirectory;

    private final File zipFile;

    final Map<String, Step> steps = new LinkedHashMap<>();
    Step currentStep;

    public MCPRuntime(Project project, File mcp_config, MCPConfigV1 config, String side,
            File mcpDirectory, Map<String, MCPFunction> extraPres) {
        this.project = project;
        this.environment = new MCPEnvironment(this, config.getVersion(), side);
        this.mcpDirectory = mcpDirectory;

        this.zipFile = mcp_config;

        @SuppressWarnings("unchecked")
        Map<String, String> data = config.getData().entrySet().stream().collect(Collectors.toMap(Entry::getKey,
            e -> e.getValue() instanceof Map ? ((Map<String, String>)e.getValue()).get(side) : (String)e.getValue()
        ));

        List<MCPConfigV1.Step> steps = config.getSteps(side);
        if (steps.isEmpty())
            throw new IllegalArgumentException("Unknown side: " + side + " For Config: " + mcp_config);

        boolean hasDownloadJson = false; //TODO: Remove when MCPConfig is published. I derped for the server -Lex
        for (MCPConfigV1.Step step : steps) {
            if (step.getName().equals("decompile")) { //TODO: Clearly define decomp vs bin, needs MCPConfig spec bump.
                if (!extraPres.isEmpty()) {
                    String input = step.getValues().get("input"); //Decompile's input
                    String lastName = null;
                    for (Entry<String, MCPFunction> entry : extraPres.entrySet()) {
                        String name = entry.getKey();
                        Map<String, String> args = new HashMap<>();
                        args.put("input", input);
                        this.steps.put(name, new Step(name, entry.getValue(), args, new File(this.mcpDirectory, name), data));
                        input = "{" + name +"Output}";
                        lastName = name;
                    }
                    step.getValues().put("input", "{" + lastName + "Output}");
                }
            }

            MCPFunction function = MCPPlugin.createBuiltInFunction(step.getType());
            if ("downloadJson".equals(step.getType())) hasDownloadJson = true;
            if ("downloadServer".equals(step.getType()) && !hasDownloadJson) {
                this.steps.put("downloadJson", new Step("downloadJson", MCPPlugin.createBuiltInFunction("downloadJson"), Collections.emptyMap(), new File(this.mcpDirectory, "downloadJson"), data));
                hasDownloadJson = true; //I derped the server side steps, hack fix until I publish a new MCPConfig -Lex
            }

            if (function == null) {
                MCPConfigV1.Function custom = config.getFunction(step.getType());
                if (custom == null)
                    throw new IllegalArgumentException("Invalid MCP Config, Unknown function step type: " + step.getType() + " File: " + mcp_config);

                File jar = MavenArtifactDownloader.manual(project, custom.getVersion(), false);
                if (jar == null || !jar.exists())
                    throw new IllegalArgumentException("Could not download MCP Config dependency: " + custom.getVersion());
                function = new ExecuteFunction(jar, custom.getJvmArgs().toArray(new String[custom.getJvmArgs().size()]),
                                                    custom.getArgs().toArray(new String[custom.getArgs().size()]), Collections.emptyMap());
            }

            File workingDir = new File(this.mcpDirectory, step.getName());
            this.steps.put(step.getName(), new Step(step.getName(), function, step.getValues(), workingDir, data));
        }
    }

    public File execute(Logger logger) throws Exception {
        return execute(logger, null);
    }

    public File executeUpTo(Logger logger, String stop) throws Exception {
        String last = null;
        for(Step step : steps.values()) {
            if (step.name.equals(stop))
                break;
            last = step.name;
        }
        return execute(logger, last);
    }

    public File execute(Logger logger, String stop) throws Exception {
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

            if (stop != null && stop.equals(step.name)) {
                logger.lifecycle("Stopping at requested step: " + ret);
                return ret;
            }
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

        private Step(String name, MCPFunction function, Map<String, String> arguments, File workingDirectory, Map<String, String> data) {
            this.name = name;
            this.function = function;
            this.arguments = new HashMap<>(arguments);
            this.workingDirectory = workingDirectory;
            function.loadData(data);
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

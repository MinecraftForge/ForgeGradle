package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;
import org.gradle.internal.impldep.com.google.gson.JsonElement;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecuteFunction implements MCPFunction {

    private static final Pattern REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)\\}$");

    private final CompletableFuture<File> jar;
    private final String[] jvmArgs;
    private final String[] runArgs;
    private final String[] envVars;

    private JsonObject data;

    public ExecuteFunction(CompletableFuture<File> jar, String[] jvmArgs, String[] runArgs, String[] envVars) {
        this.jar = jar;
        this.jvmArgs = jvmArgs;
        this.runArgs = runArgs;
        this.envVars = envVars;
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        String[] args = new String[jvmArgs.length + runArgs.length + 3];

        // Java and JVM args
        args[0] = "java";
        System.arraycopy(jvmArgs, 0, args, 1, jvmArgs.length);

        // Jar
        args[jvmArgs.length + 1] = "-jar";
        args[jvmArgs.length + 2] = jar.get().getAbsolutePath();

        // Run arguments
        System.arraycopy(runArgs, 0, args, jvmArgs.length + 3, runArgs.length);

        // Add an output and log argument if there wasn't one
        Map<String, String> arguments = environment.getArguments();
        String outputExtension = arguments.getOrDefault("outputExtension", "jar");
        arguments.computeIfAbsent("output", k -> environment.getFile("output." + outputExtension).getAbsolutePath());
        arguments.computeIfAbsent("log", k -> environment.getFile("log.log").getAbsolutePath());

        // Argument replacements
        for (int i = 0; i < args.length; i++) {
            args[i] = applyVariableSubstitutions(args[i], arguments);
        }

        // Run the command
        File workingDir = environment.getWorkingDir();
        workingDir.mkdirs();
        Runtime.getRuntime().exec(args, envVars, workingDir).waitFor();

        // Return the "output" argument
        return environment.getFile(environment.getArguments().get("output"));
    }

    @Override
    public void loadData(JsonObject data) {
        this.data = data;
    }

    private String applyVariableSubstitutions(String value, Map<String, String> arguments) {
        Matcher matcher = REPLACE_PATTERN.matcher(value);
        if (!matcher.find()) return value; // Not a replaceable string

        String argName = matcher.group(1);
        if (argName != null) {
            String argument = arguments.get(argName);
            if (argument != null) {
                return argument;
            }

            JsonElement dataElement = data.get(argName);
            if (dataElement != null) {
                return dataElement.getAsString();
            }
        }
        throw new IllegalStateException("The string '" + value + "' did not return a valid substitution match!");
    }

}

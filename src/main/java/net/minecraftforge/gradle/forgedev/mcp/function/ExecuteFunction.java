package net.minecraftforge.gradle.forgedev.mcp.function;

import org.gradle.api.Project;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExecuteFunction implements MCPFunction {

    // Matches anything in the format '{aaa}', where 'aaa' can be any combination of letters, numbers, underscores or dots
    private static final Pattern PATTERN = Pattern.compile("^\\{([\\w\\.])+\\}$");

    private final CompletableFuture<File> jar;
    private final String[] jvmArgs;
    private final String[] runArgs;
    private final String[] envVars;

    public ExecuteFunction(CompletableFuture<File> jar, String[] jvmArgs, String[] runArgs, String[] envVars) {
        this.jar = jar;
        this.jvmArgs = jvmArgs;
        this.runArgs = runArgs;
        this.envVars = envVars;
    }

    @Override
    public File execute(Project project, Map<String, String> arguments) throws Exception {
        String[] args = new String[jvmArgs.length + runArgs.length + 3];

        // Java and JVM args
        args[0] = "java";
        System.arraycopy(jvmArgs, 0, args, 1, jvmArgs.length);

        // Jar
        args[jvmArgs.length] = "-jar";
        args[jvmArgs.length + 1] = jar.get().getAbsolutePath();

        // Run arguments
        System.arraycopy(runArgs, 0, args, jvmArgs.length + 2, runArgs.length);

        // Argument replacements
        for (int i = 0; i < args.length; i++) {
            Matcher matcher = PATTERN.matcher(args[i]);
            if (matcher.find()) {
                args[i] = arguments.get(matcher.group(1));
            }
        }

        // Run the command
        Runtime.getRuntime().exec(args, envVars);

        // Return the "output" argument
        return project.file(arguments.get("output"));
    }

}

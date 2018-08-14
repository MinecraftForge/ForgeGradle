package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.gradle.api.tasks.JavaExec;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.gradle.internal.impldep.org.apache.commons.io.output.NullOutputStream;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ExecuteFunction implements MCPFunction {

    private static final Pattern REPLACE_PATTERN = Pattern.compile("^\\{(\\w+)\\}$");

    private final CompletableFuture<File> jar;
    private final String[] jvmArgs;
    private final String[] runArgs;
    private final Map<String, String> envVars;

    private JsonObject data;

    public ExecuteFunction(CompletableFuture<File> jar, String[] jvmArgs, String[] runArgs, Map<String, String> envVars) {
        this.jar = jar;
        this.jvmArgs = jvmArgs;
        this.runArgs = runArgs;
        this.envVars = envVars;
    }

    @Override
    public void loadData(JsonObject data) {
        this.data = data;
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        analyzeAndExtract(environment, zip, jvmArgs);
        analyzeAndExtract(environment, zip, runArgs);
    }

    @Override
    public File execute(MCPEnvironment environment) throws IOException, InterruptedException, ExecutionException {
        // Add an output and log argument if there wasn't one
        Map<String, String> arguments = environment.getArguments();
        String outputExtension = arguments.getOrDefault("outputExtension", "jar");
        arguments.computeIfAbsent("output", k -> environment.getFile("output." + outputExtension).getAbsolutePath());
        arguments.computeIfAbsent("log", k -> environment.getFile("log.log").getAbsolutePath());

        // Get output file
        File output = environment.getFile(environment.getArguments().get("output"));

        // Delete previous output
        if (output.exists()) output.delete();

        // Set up working directory
        File workingDir = environment.getWorkingDir();
        workingDir.mkdirs();

        // Locate main class in jar file
        File jar = this.jar.get();
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        // Execute command
        JavaExec java = environment.project.getTasks().create("_", JavaExec.class);
        java.setJvmArgs(applyVariableSubstitutions(Arrays.asList(jvmArgs), arguments));
        java.setArgs(applyVariableSubstitutions(Arrays.asList(runArgs), arguments));
        java.setClasspath(environment.project.files(jar));
        java.setWorkingDir(workingDir);
        java.setMain(mainClass);
        java.setStandardOutput(new NullOutputStream());
        java.exec();
        environment.project.getTasks().remove(java);

        // Return the output file
        return output;
    }

    private List<String> applyVariableSubstitutions(List<String> list, Map<String, String> arguments) {
        return list.stream().map(s -> applyVariableSubstitutions(s, arguments)).collect(Collectors.toList());
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

    private void analyzeAndExtract(MCPEnvironment environment, ZipFile zip, String[] args) throws IOException {
        for (String arg : args) {
            Matcher matcher = REPLACE_PATTERN.matcher(arg);
            if (!matcher.find()) continue;

            String argName = matcher.group(1);
            if (argName == null) continue;

            JsonElement dataElement = data.get(argName);
            if (dataElement == null) continue;
            String referencedData = dataElement.getAsString();

            ZipEntry entry = zip.getEntry(referencedData);
            if (entry == null) continue;
            String entryName = entry.getName();

            if (entry.isDirectory()) {
                Utils.extractDirectory(environment::getFile, zip, entryName);
            } else {
                Utils.extractFile(zip, entry, environment.getFile(entryName));
            }
        }
    }

}

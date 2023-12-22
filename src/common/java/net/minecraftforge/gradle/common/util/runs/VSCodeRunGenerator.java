/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class VSCodeRunGenerator extends RunConfigGenerator.JsonConfigurationBuilder {
    @Override
    protected JsonObject createRunConfiguration(Project project, RunConfig runConfig, List<String> additionalClientArgs,
            FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts) {
        Map<String, Supplier<String>> updatedTokens = configureTokensLazy(project,
                runConfig,
                mapModClassesToVSCode(project, runConfig),
                minecraftArtifacts, runtimeClasspathArtifacts);

        JsonObject config = new JsonObject();
        config.addProperty("type", "java");
        config.addProperty("name", runConfig.getTaskName());
        config.addProperty("request", "launch");
        config.addProperty("mainClass", runConfig.getMain());
        config.addProperty("projectName", EclipseRunGenerator.getEclipseProjectName(project));
        config.addProperty("cwd", replaceRootDirBy(project, runConfig.getWorkingDirectory(), "${workspaceFolder}"));
        config.addProperty("vmArgs", getJvmArgs(runConfig, additionalClientArgs, updatedTokens));
        config.addProperty("args", getArgs(runConfig, updatedTokens));
        JsonObject env = new JsonObject();
        runConfig.getEnvironment().forEach((key, value) -> {
            value = runConfig.replace(updatedTokens, value);
            if (key.equals("nativesDirectory"))
                value = replaceRootDirBy(project, value, "${workspaceFolder}");
            env.addProperty(key, value);
        });
        config.add("env", env);
        config.addProperty("preLaunchTask", runConfig.getPrepareCompileTaskName());
        return config;
    }

    @Override
    protected JsonObject createPrepareTaskConfiguration(Project project, RunConfig runConfig) {
        JsonObject config = new JsonObject();
        config.addProperty("label", runConfig.getPrepareCompileTaskName());
        config.addProperty("type", "shell");
        config.addProperty("command", "./gradlew " + runConfig.getPrepareCompileTaskName());
        JsonObject options = new JsonObject();
        options.addProperty("cwd", "${workspaceFolder}");
        config.add("options", options);
        return config;
    }

    private Stream<String> mapModClassesToVSCode(Project project, RunConfig runConfig) {
        return IntellijRunGenerator.mapModClassesToGradle(project, runConfig)
                .map((value) -> replaceRootDirBy(project, value, "${workspaceFolder}"));
    }
}

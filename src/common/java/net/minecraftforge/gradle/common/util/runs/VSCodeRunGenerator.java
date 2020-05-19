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

package net.minecraftforge.gradle.common.util.runs;

import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class VSCodeRunGenerator extends RunConfigGenerator.JsonConfigurationBuilder
{
    @Nonnull
    @Override
    protected JsonObject createRunConfiguration(@Nonnull Project project, @Nonnull RunConfig runConfig, List<String> additionalClientArgs)
    {
        Map<String, String> updatedTokens = configureTokens(runConfig, mapModClassesToVSCode(project, runConfig));

        JsonObject config = new JsonObject();
        config.addProperty("type", "java");
        config.addProperty("name", runConfig.getTaskName());
        config.addProperty("request", "launch");
        config.addProperty("mainClass", runConfig.getMain());
        config.addProperty("projectName", project.getName());
        config.addProperty("cwd", replaceRootDirBy(project, runConfig.getWorkingDirectory(), "${workspaceFolder}"));
        config.addProperty("vmArgs", getJvmArgs(runConfig, additionalClientArgs, updatedTokens));
        config.addProperty("args", getArgs(runConfig, updatedTokens));
        JsonObject env = new JsonObject();
        runConfig.getEnvironment().forEach((key,value) -> {
            value = runConfig.replace(updatedTokens, value);
            if (key.equals("nativesDirectory"))
                value = replaceRootDirBy(project, value, "${workspaceFolder}");
            env.addProperty(key, value);
        });
        config.add("env", env);
        return config;
    }

    private Stream<String> mapModClassesToVSCode(@Nonnull Project project, @Nonnull RunConfig runConfig)
    {
        return EclipseRunGenerator.mapModClassesToEclipse(project, runConfig)
            .map((value) -> replaceRootDirBy(project, value, "${workspaceFolder}"));
    }
}

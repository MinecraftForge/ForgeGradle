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

import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EclipseRunGenerator extends RunConfigGenerator.XMLConfigurationBuilder
{
    @Override
    @Nonnull
    protected Map<String, Document> createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder documentBuilder, List<String> additionalClientArgs) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        Map<String, String> updatedTokens = configureTokens(runConfig, mapModClassesToEclipse(project, runConfig));

        final File workingDirectory = new File(runConfig.getWorkingDirectory());

        // Eclipse requires working directory to exist
        if (!workingDirectory.exists()) {
            workingDirectory.mkdirs();
        }

        // Java run config
        final Document javaDocument = documentBuilder.newDocument();
        {
            final Element rootElement = javaDocument.createElement("launchConfiguration");
            {
                rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");

                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROJECT_ATTR", project.getName());
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.MAIN_TYPE", runConfig.getMain());
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.VM_ARGUMENTS",
                        getJvmArgs(runConfig, additionalClientArgs, updatedTokens));
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS",
                        getArgs(runConfig, updatedTokens));
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.WORKING_DIRECTORY", runConfig.getWorkingDirectory());

                final Element envs = javaDocument.createElement("mapAttribute");
                {
                    envs.setAttribute("key", "org.eclipse.debug.core.environmentVariables");

                    runConfig.getEnvironment().forEach((name, value) -> {
                        final Element envEntry = javaDocument.createElement("mapEntry");
                        {
                            envEntry.setAttribute("key", name);
                            envEntry.setAttribute("value", runConfig.replace(updatedTokens, value));
                        }
                        envs.appendChild(envEntry);
                    });
                }
                rootElement.appendChild(envs);
            }
            javaDocument.appendChild(rootElement);
        }
        documents.put(runConfig.getTaskName() + ".launch", javaDocument);

        return documents;
    }

    static Stream<String> mapModClassesToEclipse(@Nonnull final Project project, @Nonnull final RunConfig runConfig) {
        final EclipseModel eclipse = project.getExtensions().findByType(EclipseModel.class);

        final Map<String, String> outputs = eclipse.getClasspath().resolveDependencies().stream()
                .filter(SourceFolder.class::isInstance)
                .map(SourceFolder.class::cast)
                .map(SourceFolder::getOutput)
                .distinct()
                .collect(Collectors.toMap(output -> output.split("/")[output.split("/").length - 1], output -> project.file(output).getAbsolutePath()));

        if (runConfig.getMods().isEmpty()) {
            return runConfig.getAllSources().stream()
                    .map(SourceSet::getName)
                    .filter(outputs::containsKey)
                    .map(outputs::get)
                    .map(s -> String.join(File.pathSeparator, s, s)); // <resources>:<classes>
        } else {
            final SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            return runConfig.getMods().stream()
                    .map(modConfig -> {
                        return (modConfig.getSources().isEmpty() ? Stream.of(main) : modConfig.getSources().stream())
                                .map(SourceSet::getName)
                                .filter(outputs::containsKey)
                                .map(outputs::get)
                                .map(output -> modConfig.getName() + "%%" + output)
                                .map(s -> String.join(File.pathSeparator, s, s)); // <resources>:<classes>
                    }).flatMap(Function.identity());
        }
    }
}

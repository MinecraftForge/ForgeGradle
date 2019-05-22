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

package net.minecraftforge.gradle.common.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IDEUtils {

    @SuppressWarnings("UnstableApiUsage")
    public static void createIDEGenRunsTasks(@Nonnull final MinecraftExtension minecraft, @Nonnull final TaskProvider<Task> prepareRuns, @Nonnull final TaskProvider<Task> makeSourceDirs) {
        final Project project = minecraft.getProject();

        final Map<String, Triple<List<Object>, File, RunConfigurationGenerator>> ideConfigurationGenerators = ImmutableMap.<String, Triple<List<Object>, File, RunConfigurationGenerator>>builder()
                .put("genIntellijRuns", ImmutableTriple.of(Collections.singletonList(prepareRuns.get()),
                        new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"), IDEUtils::createIntellijRunConfigurationXML))
                .put("genEclipseRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns.get(), makeSourceDirs.get()),
                        project.getProjectDir(), IDEUtils::createEclipseRunConfigurationXML))
                .build();

        ideConfigurationGenerators.forEach((taskName, configurationGenerator) -> {
            project.getTasks().register(taskName, Task.class, task -> {
                task.setGroup(RunConfig.RUNS_GROUP);
                task.dependsOn(configurationGenerator.getLeft());

                task.doLast(t -> {
                    try {
                        final File runConfigurationsDir = configurationGenerator.getMiddle();

                        if (!runConfigurationsDir.exists()) {
                            runConfigurationsDir.mkdirs();
                        }

                        final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                        final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                        final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                        final Transformer transformer = transformerFactory.newTransformer();

                        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                        minecraft.getRuns().forEach(runConfig -> {
                            final Stream<String> propStream = runConfig.getProperties().entrySet().stream().map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));
                            final String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
                            final Map<String, Document> documents = configurationGenerator.getRight().createRunConfigurationXML(project, runConfig, props, docBuilder);

                            documents.forEach((fileName, document) -> {
                                final DOMSource source = new DOMSource(document);
                                final StreamResult result = new StreamResult(new File(runConfigurationsDir, fileName));

                                try {
                                    transformer.transform(source, result);
                                } catch (TransformerException e) {
                                    e.printStackTrace();
                                }
                            });
                        });
                    } catch (ParserConfigurationException | TransformerConfigurationException e) {
                        e.printStackTrace();
                    }
                });
            });
        });
    }

    private static void elementOption(@Nonnull Document document, @Nonnull final Element parent, @Nonnull final String name, @Nonnull final String value) {
        final Element option = document.createElement("option");
        {
            option.setAttribute("name", name);
            option.setAttribute("value", value);
        }
        parent.appendChild(option);
    }

    @Nonnull
    private static Map<String, Document> createIntellijRunConfigurationXML(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        // Java run config
        final Document javaDocument = documentBuilder.newDocument();
        {
            final Element rootElement = javaDocument.createElement("component");
            {
                final Element configuration = javaDocument.createElement("configuration");
                {
                    configuration.setAttribute("default", "false");
                    configuration.setAttribute("name", runConfig.getUniqueName());
                    configuration.setAttribute("type", "Application");
                    configuration.setAttribute("factoryName", "Application");
                    configuration.setAttribute("singleton", runConfig.isSingleInstance() ? "true" : "false");

                    elementOption(javaDocument, configuration, "MAIN_CLASS_NAME", runConfig.getMain());
                    elementOption(javaDocument, configuration, "VM_PARAMETERS", props);
                    elementOption(javaDocument, configuration, "PROGRAM_PARAMETERS", String.join(" ", runConfig.getArgs()));
                    elementOption(javaDocument, configuration, "WORKING_DIRECTORY", runConfig.getWorkingDirectory());

                    final Element module = javaDocument.createElement("module");
                    {
                        module.setAttribute("name", runConfig.getIdeaModule());
                    }
                    configuration.appendChild(module);

                    final Element envs = javaDocument.createElement("envs");
                    {
                        runConfig.getEnvironment().forEach((name, value) -> {
                            final Element envEntry = javaDocument.createElement("env");
                            {
                                envEntry.setAttribute("name", name);
                                envEntry.setAttribute("value", value);
                            }
                            envs.appendChild(envEntry);
                        });
                    }
                    configuration.appendChild(envs);

                    final Element methods = javaDocument.createElement("method");
                    {
                        methods.setAttribute("v", "2");

                        final Element makeTask = javaDocument.createElement("option");
                        {
                            makeTask.setAttribute("name", "Make");
                            makeTask.setAttribute("enabled", "true");
                        }
                        methods.appendChild(makeTask);

                        final Element gradleTask = javaDocument.createElement("option");
                        {
                            gradleTask.setAttribute("name", "Gradle.BeforeRunTask");
                            gradleTask.setAttribute("enabled", "true");
                            gradleTask.setAttribute("tasks", project.getTasks().getByName("prepare" + Utils.capitalize(runConfig.getTaskName())).getPath());
                            gradleTask.setAttribute("externalProjectPath", "$PROJECT_DIR$");
                        }
                        methods.appendChild(gradleTask);
                    }
                    configuration.appendChild(methods);
                }
                rootElement.appendChild(configuration);
            }
            javaDocument.appendChild(rootElement);
        }
        documents.put(runConfig.getUniqueFileName() + ".xml", javaDocument);

        return documents;
    }

    private static void elementAttribute(@Nonnull Document document, @Nonnull final Element parent, @Nonnull final String attributeType, @Nonnull final String key, @Nonnull final String value) {
        final Element attribute = document.createElement(attributeType + "Attribute");
        {
            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value);
        }
        parent.appendChild(attribute);
    }

    @Nonnull
    private static Map<String, Document> createEclipseRunConfigurationXML(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        // Java run config
        final Document javaDocument = documentBuilder.newDocument();
        {
            final Element rootElement = javaDocument.createElement("launchConfiguration");
            {
                rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");

                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROJECT_ATTR", project.getName());
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.MAIN_TYPE", runConfig.getMain());
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.VM_ARGUMENTS", props);
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", String.join(" ", runConfig.getArgs()));

                final File workingDirectory = new File(runConfig.getWorkingDirectory());

                // Eclipse requires working directory to exist
                if (!workingDirectory.exists()) {
                    workingDirectory.mkdirs();
                }

                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.WORKING_DIRECTORY", runConfig.getWorkingDirectory());

                final Element envs = javaDocument.createElement("mapAttribute");
                {
                    envs.setAttribute("key", "org.eclipse.debug.core.environmentVariables");

                    runConfig.getEnvironment().compute("MOD_CLASSES", (key, value) -> {
                        // Only replace environment variable if it is already set
                        if (value == null || value.isEmpty()) {
                            return value;
                        }

                        final EclipseModel eclipse = project.getExtensions().findByType(EclipseModel.class);

                        if (eclipse != null) {
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
                                        .map(s -> String.join(File.pathSeparator, s, s)) // <resources>:<classes>
                                        .collect(Collectors.joining(File.pathSeparator));
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
                                        })
                                        .flatMap(Function.identity())
                                        .collect(Collectors.joining(File.pathSeparator));
                            }
                        }

                        return value;
                    });

                    runConfig.getEnvironment().forEach((name, value) -> {
                        final Element envEntry = javaDocument.createElement("mapEntry");
                        {
                            envEntry.setAttribute("key", name);
                            envEntry.setAttribute("value", value);
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

    @FunctionalInterface
    private interface RunConfigurationGenerator {

        @Nonnull
        Map<String, Document> createRunConfigurationXML(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder);

    }

}

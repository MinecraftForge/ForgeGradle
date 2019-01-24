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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
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
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntellijUtils {

    @SuppressWarnings("UnstableApiUsage")
    public static void createIntellijRunsTask(@Nonnull final MinecraftExtension minecraft, @Nonnull final TaskProvider<Task> prepareRuns) {
        final Project project = minecraft.getProject();

        project.getTasks().register("genIntelliJRuns", Task.class, task -> {
            task.dependsOn(prepareRuns.get());

            try {
                final File runConfigurationsDir = new File(project.getRootProject().getRootDir(), ".idea/runConfigurations");

                if (!runConfigurationsDir.exists()) {
                    runConfigurationsDir.mkdirs();
                }

                DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                TransformerFactory transformerFactory = TransformerFactory.newInstance();
                Transformer transformer = transformerFactory.newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                minecraft.getRuns().forEach(runConfig -> createRunConfigurationXML(project, runConfig, docBuilder, transformer, runConfigurationsDir));
            } catch (ParserConfigurationException | TransformerConfigurationException e) {
                e.printStackTrace();
            }
        });
    }

    public static void createRunConfigurationXML(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder docBuilder, @Nonnull final Transformer transformer, @Nonnull final File runConfigurationsDir) {
        Stream<String> propStream = runConfig.getProperties().entrySet().stream()
                .map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));
        String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
        Document doc = docBuilder.newDocument();
        {
            Element rootElement = doc.createElement("component");
            {
                Element configuration = doc.createElement("configuration");
                {
                    configuration.setAttribute("default", "false");
                    configuration.setAttribute("name", runConfig.getUniqueName());
                    configuration.setAttribute("type", "Application");
                    configuration.setAttribute("factoryName", "Application");
                    configuration.setAttribute("singleton", runConfig.isSingleInstance() ? "true" : "false");

                    Element className = doc.createElement("option");
                    {
                        className.setAttribute("name", "MAIN_CLASS_NAME");
                        className.setAttribute("value", runConfig.getMain());
                    }
                    configuration.appendChild(className);

                    Element vmParameters = doc.createElement("option");
                    {
                        vmParameters.setAttribute("name", "VM_PARAMETERS");
                        vmParameters.setAttribute("value", props);
                    }
                    configuration.appendChild(vmParameters);

                    Element programParameters = doc.createElement("option");
                    {
                        programParameters.setAttribute("name", "PROGRAM_PARAMETERS");
                        programParameters.setAttribute("value", String.join(" ", runConfig.getArgs()));
                    }
                    configuration.appendChild(programParameters);

                    Element workingDirectory = doc.createElement("option");
                    {
                        workingDirectory.setAttribute("name", "WORKING_DIRECTORY");
                        workingDirectory.setAttribute("value", runConfig.getWorkingDirectory());
                    }
                    configuration.appendChild(workingDirectory);

                    Element module = doc.createElement("module");
                    {
                        module.setAttribute("name", runConfig.getIdeaModule());
                    }
                    configuration.appendChild(module);

                    Element envs = doc.createElement("envs");
                    runConfig.getEnvironment().forEach((name, value) -> {
                        Element envEntry = doc.createElement("env");
                        {
                            envEntry.setAttribute("name", name);
                            envEntry.setAttribute("value", value);
                        }
                        envs.appendChild(envEntry);
                    });
                    configuration.appendChild(envs);

                    Element methods = doc.createElement("method");
                    {
                        methods.setAttribute("v", "2");

                        Element makeTask = doc.createElement("option");
                        {
                            makeTask.setAttribute("name", "Make");
                            makeTask.setAttribute("enabled", "true");
                        }
                        methods.appendChild(makeTask);

                        Element gradleTask = doc.createElement("option");
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
            doc.appendChild(rootElement);
        }

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(runConfigurationsDir, runConfig.getUniqueFileName() + ".xml"));

        try {
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

}

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

import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

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
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IntellijUtils {
    public static void createIntellijRunsTask(Project project, ExtractNatives extractNatives, DownloadAssets downloadAssets, Task prepareRun, Map<String, RunConfig> runs) {
        TaskProvider<Task> genIntellijRuns = project.getTasks().register("genIntellijRuns", Task.class);
        genIntellijRuns.configure(task0 -> {
            task0.dependsOn(extractNatives, downloadAssets);
            task0.doLast(task1 -> {
                try {
                    File runConfigurationsDir = new File(project.getRootProject().getProjectDir().getCanonicalFile(), ".idea/runConfigurations");

                    if (!runConfigurationsDir.exists())
                        runConfigurationsDir.mkdirs();

                    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
                    runs.forEach((name, runConfig) -> {
                        String moduleName = runConfig.getIdeaModule();
                        if (moduleName == null)
                            moduleName = project.getName() + "_main";
                        createRunConfigurationXml(name, runConfig, runConfig.isSingleInstance(), docBuilder, transformer,
                                    moduleName, Collections.singletonList(prepareRun), runConfigurationsDir);
                    });

                } catch (IOException | ParserConfigurationException | TransformerConfigurationException e) {
                    e.printStackTrace();
                }
            });
        });
    }

    public static void createRunConfigurationXml(Project project, String runName, RunConfig runConfig, boolean singleInstance, String moduleName, Collection<Task> dependencyTasks) {
        try {
            File runConfigurationsDir = new File(project.getProjectDir().getCanonicalFile(), ".idea/runConfigurations");

            if (!runConfigurationsDir.exists())
                runConfigurationsDir.mkdirs();

            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            createRunConfigurationXml(runName, runConfig, singleInstance, docBuilder, transformer, moduleName, dependencyTasks, runConfigurationsDir);
        } catch (IOException | ParserConfigurationException | TransformerConfigurationException e) {
            e.printStackTrace();
        }
    }

    private static void createRunConfigurationXml(String taskName, RunConfig runConfig, boolean singleInstance, DocumentBuilder docBuilder, Transformer transformer, String moduleName, Collection<Task> dependencyTasks, File runConfigurationsDir) {
        String mainClass = runConfig.getMain();
        String workDir = runConfig.getWorkingDirectory();
        Stream<String> propStream = runConfig.getProperties().entrySet().stream()
                .map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));

        String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
        String args = String.join(" ", runConfig.getArgs());

        Document doc = docBuilder.newDocument();

        Element rootElement = doc.createElement("component");
        rootElement.setAttribute("name", "ProjectRunConfigurationManager");
        doc.appendChild(rootElement);

        Element configuration = doc.createElement("configuration");
        configuration.setAttribute("default", "false");
        configuration.setAttribute("name", taskName);
        configuration.setAttribute("type", "Application");
        configuration.setAttribute("factoryName", "Application");
        configuration.setAttribute("singleton", singleInstance ? "true" : "false");
        rootElement.appendChild(configuration);

        Element className = doc.createElement("option");
        className.setAttribute("name", "MAIN_CLASS_NAME");
        className.setAttribute("value", mainClass);
        configuration.appendChild(className);

        Element vmParameters = doc.createElement("option");
        vmParameters.setAttribute("name", "VM_PARAMETERS");
        vmParameters.setAttribute("value", props);
        configuration.appendChild(vmParameters);

        Element programParameters = doc.createElement("option");
        programParameters.setAttribute("name", "PROGRAM_PARAMETERS");
        programParameters.setAttribute("value", args);
        configuration.appendChild(programParameters);

        Element workingDirectory = doc.createElement("option");
        workingDirectory.setAttribute("name", "WORKING_DIRECTORY");
        workingDirectory.setAttribute("value", workDir);
        configuration.appendChild(workingDirectory);

        Element module = doc.createElement("module");
        module.setAttribute("name", moduleName);
        configuration.appendChild(module);

        Element envs = doc.createElement("envs");
        configuration.appendChild(envs);

        runConfig.getEnvironment().entrySet().forEach(kvp -> {

            Element envEntry = doc.createElement("env");
            envEntry.setAttribute("name", kvp.getKey());
            envEntry.setAttribute("value", kvp.getValue());
            envs.appendChild(envEntry);
        });

        Element methods = doc.createElement("method");
        methods.setAttribute("v", "2");
        configuration.appendChild(methods);

        Element makeTask = doc.createElement("option");
        makeTask.setAttribute("name", "Make");
        makeTask.setAttribute("enabled", "true");
        methods.appendChild(makeTask);

        dependencyTasks.forEach(dependencyTask -> {
            Element gradleTask = doc.createElement("option");
            gradleTask.setAttribute("name", "Gradle.BeforeRunTask");
            gradleTask.setAttribute("enabled", "true");
            gradleTask.setAttribute("tasks", dependencyTask.getName());
            gradleTask.setAttribute("externalProjectPath", "$PROJECT_DIR$");
            methods.appendChild(gradleTask);
        });

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(runConfigurationsDir, taskName + ".xml"));
        try {
            transformer.transform(source, result);
        } catch (TransformerException e) {
            e.printStackTrace();
        }

    }
}

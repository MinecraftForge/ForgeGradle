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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
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

        final Map<String, Triple<List<Object>, File, RunConfigurationBuilder>> ideConfigurationGenerators = ImmutableMap.<String, Triple<List<Object>, File, RunConfigurationBuilder>>builder()
                .put("genIntellijRuns", ImmutableTriple.of(Collections.singletonList(prepareRuns.get()),
                        new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"), new XMLConfigurationBuilder(IDEUtils::createIntellijRunConfigurationXML)))
                .put("genEclipseRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns.get(), makeSourceDirs.get()),
                        project.getProjectDir(), new XMLConfigurationBuilder(IDEUtils::createEclipseRunConfigurationXML)))
                .put("genVSCodeRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns.get(), makeSourceDirs.get()),
                        new File(project.getProjectDir(), ".vscode"), new JsonConfigurationBuilder(IDEUtils::createVSCodeRunConfiguration)))
                .build();

        ideConfigurationGenerators.forEach((taskName, configurationGenerator) -> {
            project.getTasks().register(taskName, Task.class, task -> {
                task.setGroup(RunConfig.RUNS_GROUP);
                task.dependsOn(configurationGenerator.getLeft());

                task.doLast(t -> {
                    final File runConfigurationsDir = configurationGenerator.getMiddle();

                    if (!runConfigurationsDir.exists()) {
                        runConfigurationsDir.mkdirs();
                    }
                    configurationGenerator.getRight().createConfig(minecraft, runConfigurationsDir, project);
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

                    runConfig.getEnvironment().compute("MOD_CLASSES", (key, value) -> mapModClasses(key, value, project, runConfig));
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

    @Nonnull
    private static JsonObject createVSCodeRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props) {
        JsonObject config = new JsonObject();
        config.addProperty("type", "java");
        config.addProperty("name", runConfig.getTaskName());
        config.addProperty("request", "launch");
        config.addProperty("mainClass", runConfig.getMain());
        config.addProperty("projectName", project.getName());
        config.addProperty("cwd", replaceRootDirBy(project, runConfig.getWorkingDirectory().toString(), "${workspaceFolder}"));
        config.addProperty("vmArgs", props);
        config.addProperty("args", String.join(" ", runConfig.getArgs()));
        JsonObject env = new JsonObject();
        runConfig.getEnvironment().compute("MOD_CLASSES", (key, value) -> replaceRootDirBy(project, mapModClasses(key, value, project, runConfig), "${workspaceFolder}"));
        runConfig.getEnvironment().compute("nativesDirectory", (key, value) -> replaceRootDirBy(project, value, "${workspaceFolder}"));
        runConfig.getEnvironment().forEach((name, value) -> {
            env.addProperty(name, value);
        });
        config.add("env", env);
        return config;
    }

    private static String replaceRootDirBy(@Nonnull final Project project, String value, @Nonnull final String replacement) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replace(project.getRootDir().toString(), replacement);
    }

    private static String mapModClasses(String key, String value, @Nonnull final Project project, @Nonnull final RunConfig runConfig) {
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
    }

    @FunctionalInterface
    private interface XMLRunConfigurationGenerator {

        @Nonnull
        Map<String, Document> createRunConfigurationXML(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder);

    }

    @FunctionalInterface
    private interface JsonRunConfigurationGenerator {

        @Nonnull
        JsonObject createRunConfigurationJson(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props);

    }

    private interface RunConfigurationBuilder {

        void createConfig(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project);

    }

    private static class XMLConfigurationBuilder implements RunConfigurationBuilder {

        private XMLRunConfigurationGenerator configGenerator;

        public XMLConfigurationBuilder(XMLRunConfigurationGenerator generator) {
            configGenerator = generator;
        }

        @Override
        public void createConfig(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project) {
            try {
                final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                final Transformer transformer = transformerFactory.newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                minecraft.getRuns().forEach(runConfig -> {
                    final Stream<String> propStream = runConfig.getProperties().entrySet().stream().map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));
                    final String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
                    final Map<String, Document> documents = configGenerator.createRunConfigurationXML(project, runConfig, props, docBuilder);

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
        }
    }

    private static class JsonConfigurationBuilder implements RunConfigurationBuilder {

        private JsonRunConfigurationGenerator configGenerator;

        public JsonConfigurationBuilder(JsonRunConfigurationGenerator generator) {
            configGenerator = generator;
        }

        @Override
        public void createConfig(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project) {
            final JsonObject rootObject = new JsonObject();
            rootObject.addProperty("version", "0.2.0");
            JsonArray runConfigs = new JsonArray();
            minecraft.getRuns().forEach(runConfig -> {
                final Stream<String> propStream = runConfig.getProperties().entrySet().stream().map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));
                final String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
                runConfigs.add(configGenerator.createRunConfigurationJson(project, runConfig, props));
            });
            rootObject.add("configurations", runConfigs);
            Writer writer;
            try {
                writer = new FileWriter(new File(runConfigurationsDir, "launch.json"));
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                writer.write(gson.toJson(rootObject));
                writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

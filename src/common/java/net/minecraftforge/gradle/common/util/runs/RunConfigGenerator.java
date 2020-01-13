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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.ModConfig;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class RunConfigGenerator
{
    public abstract void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project);

    @SuppressWarnings("UnstableApiUsage")
    public static void createIDEGenRunsTasks(@Nonnull final MinecraftExtension minecraft, @Nonnull final TaskProvider<Task> prepareRuns, @Nonnull final TaskProvider<Task> makeSourceDirs) {
        final Project project = minecraft.getProject();

        final Map<String, Triple<List<Object>, File, RunConfigGenerator>> ideConfigurationGenerators = ImmutableMap.<String, Triple<List<Object>, File, RunConfigGenerator>>builder()
                .put("genIntellijRuns", ImmutableTriple.of(Collections.singletonList(prepareRuns.get()),
                        new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"),
                        new IntellijRunGenerator()))
                .put("genEclipseRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns.get(), makeSourceDirs.get()),
                        project.getProjectDir(),
                        new EclipseRunGenerator()))
                .put("genVSCodeRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns.get(), makeSourceDirs.get()),
                        new File(project.getProjectDir(), ".vscode"),
                        new VSCodeRunGenerator()))
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
                    configurationGenerator.getRight().createRunConfiguration(minecraft, runConfigurationsDir, project);
                });
            });
        });
    }

    protected static void elementOption(@Nonnull Document document, @Nonnull final Element parent, @Nonnull final String name, @Nonnull final String value) {
        final Element option = document.createElement("option");
        {
            option.setAttribute("name", name);
            option.setAttribute("value", value);
        }
        parent.appendChild(option);
    }

    protected static void elementAttribute(@Nonnull Document document, @Nonnull final Element parent, @Nonnull final String attributeType, @Nonnull final String key, @Nonnull final String value) {
        final Element attribute = document.createElement(attributeType + "Attribute");
        {
            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value);
        }
        parent.appendChild(attribute);
    }

    protected static String replaceRootDirBy(@Nonnull final Project project, String value, @Nonnull final String replacement) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.replace(project.getRootDir().toString(), replacement);
    }

    protected static String mapModClassesToGradle(Project project, RunConfig runConfig)
    {
        if (runConfig.getMods().isEmpty()) {
            List<SourceSet> sources = runConfig.getAllSources();
            return Stream.concat(
                    sources.stream().map(source -> source.getOutput().getResourcesDir()),
                    sources.stream().map(source -> source.getOutput().getClassesDirs().getFiles()).flatMap(Collection::stream)
            ).map(File::getAbsolutePath).collect(Collectors.joining(File.pathSeparator));
        } else {
            final SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            return runConfig.getMods().stream()
                    .map(modConfig -> {
                        return (modConfig.getSources().isEmpty() ? Stream.of(main) : modConfig.getSources().stream())
                                .flatMap(source -> Streams
                                        .concat(Stream.of(source.getOutput().getResourcesDir()),
                                                source.getOutput().getClassesDirs().getFiles().stream()))
                                .map(File::getAbsolutePath)
                                .distinct()
                                .map(s -> modConfig.getName() + "%%" + s)
                                .collect(Collectors.joining(File.pathSeparator)); // <resources>:<classes>
                    })
                    .collect(Collectors.joining(File.pathSeparator));
        }
    }

    public static String configureTokens(Project project, ModConfig modConfig, @Nonnull final Map<String, String> tokens) {
        final SourceSet main = project.getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Stream<String> modClasses =
                Stream.concat((!modConfig.hasResources() ? Stream.of(main.getOutput().getResourcesDir()) : modConfig.getResources().getFiles().stream()),
                (!modConfig.hasClasses() ? main.getOutput().getClassesDirs().getFiles() : modConfig.getClasses().getFiles()).stream())
                .distinct()
                .map(file -> modConfig.getName() + "%%" + file.getAbsolutePath());

        if (tokens.containsKey("source_roots")) {
            modClasses = Stream.concat(Arrays.stream(tokens.get("source_roots").split(File.pathSeparator)), modClasses);
        }

        return modClasses.distinct().collect(Collectors.joining(File.pathSeparator));
    }

    @SuppressWarnings("UnstableApiUsage")
    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final TaskProvider<Task> prepareRuns, final List<String> additionalClientArgs) {
        return createRunTask(runConfig, project, prepareRuns.get(), additionalClientArgs);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final Task prepareRuns, final List<String> additionalClientArgs) {

        runConfig.replaceTokens();

        TaskProvider<Task> prepareRun = project.getTasks().register("prepare" + Utils.capitalize(runConfig.getTaskName()), Task.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(prepareRuns, runConfig.getAllSources().stream().map(SourceSet::getClassesTaskName).toArray());

            File workDir = new File(runConfig.getWorkingDirectory());

            if (!workDir.exists()) {
                workDir.mkdirs();
            }
        });

        return project.getTasks().register(runConfig.getTaskName(), JavaExec.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(prepareRun.get());

            File workDir = new File(runConfig.getWorkingDirectory());

            if (!workDir.exists()) {
                workDir.mkdirs();
            }

            task.setWorkingDir(workDir);
            task.setMain(runConfig.getMain());

            task.args(runConfig.getArgs());
            task.jvmArgs(runConfig.getJvmArgs());
            if (runConfig.isClient()) {
                task.jvmArgs(additionalClientArgs);
            }
            Map<String, String> environment = Maps.newHashMap(runConfig.getEnvironment());
            environment.putIfAbsent("MOD_CLASSES", mapModClassesToGradle(project, runConfig));
            task.environment(environment);
            task.systemProperties(runConfig.getProperties());

            runConfig.getAllSources().stream().map(SourceSet::getRuntimeClasspath).forEach(task::classpath);

            // Stop after this run task so it doesn't try to execute the run tasks, and their dependencies, of sub projects
            task.doLast(t -> System.exit(0)); // TODO: Find better way to stop gracefully
        });
    }

    static abstract class XMLConfigurationBuilder extends RunConfigGenerator {

        @Nonnull
        protected abstract Map<String, Document> createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props, @Nonnull final DocumentBuilder documentBuilder);

        @Override
        public final void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project) {
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
                    final Map<String, Document> documents = createRunConfiguration(project, runConfig, props, docBuilder);

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

    static abstract class JsonConfigurationBuilder extends RunConfigGenerator {

        @Nonnull
        protected abstract JsonObject createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props);

        @Override
        public final void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project) {
            final JsonObject rootObject = new JsonObject();
            rootObject.addProperty("version", "0.2.0");
            JsonArray runConfigs = new JsonArray();
            minecraft.getRuns().forEach(runConfig -> {
                final Stream<String> propStream = runConfig.getProperties().entrySet().stream().map(kv -> String.format("-D%s=%s", kv.getKey(), kv.getValue()));
                final String props = Stream.concat(propStream, runConfig.getJvmArgs().stream()).collect(Collectors.joining(" "));
                runConfigs.add(createRunConfiguration(project, runConfig, props));
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

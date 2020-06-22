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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class RunConfigGenerator
{
    public abstract void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project, List<String> additionalClientArgs);

    @SuppressWarnings("UnstableApiUsage")
    public static void createIDEGenRunsTasks(@Nonnull final MinecraftExtension minecraft, @Nonnull final TaskProvider<Task> prepareRuns, @Nonnull final TaskProvider<Task> makeSourceDirs, List<String> additionalClientArgs) {
        final Project project = minecraft.getProject();

        final Map<String, Triple<List<Object>, File, RunConfigGenerator>> ideConfigurationGenerators = ImmutableMap.<String, Triple<List<Object>, File, RunConfigGenerator>>builder()
                .put("genIntellijRuns", ImmutableTriple.of(Collections.singletonList(prepareRuns.get()),
                        new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"),
                        new IntellijRunGenerator(project.getRootProject())))
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
                    configurationGenerator.getRight().createRunConfiguration(minecraft, runConfigurationsDir, project, additionalClientArgs);
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

    protected static Stream<String> mapModClassesToGradle(Project project, RunConfig runConfig)
    {
        if (runConfig.getMods().isEmpty()) {
            List<SourceSet> sources = runConfig.getAllSources();
            return Stream.concat(
                    sources.stream().map(source -> source.getOutput().getResourcesDir()),
                    sources.stream().map(source -> source.getOutput().getClassesDirs().getFiles()).flatMap(Collection::stream)
            ).map(File::getAbsolutePath);
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
                    });
        }
    }

    protected static Map<String,String> configureTokens(@Nonnull RunConfig runConfig, Stream<String> modClasses) {
        Map<String, String> tokens = new HashMap<>(runConfig.getTokens());
        tokens.compute("source_roots", (key,sourceRoots) -> ((sourceRoots != null)
                ? Stream.concat(Arrays.stream(sourceRoots.split(File.pathSeparator)), modClasses)
                : modClasses).distinct().collect(Collectors.joining(File.pathSeparator)));

        // *Grumbles about having to keep a workaround for a "dummy" hack that should have never existed*
        runConfig.getEnvironment().compute("MOD_CLASSES", (key,value) ->
                Strings.isNullOrEmpty(value) || "dummy".equals(value) ? "{source_roots}" : value);

        return tokens;
    }

    @SuppressWarnings("UnstableApiUsage")
    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final TaskProvider<Task> prepareRuns, final List<String> additionalClientArgs) {
        return createRunTask(runConfig, project, prepareRuns.get(), additionalClientArgs);
    }

    @SuppressWarnings("UnstableApiUsage")
    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final Task prepareRuns, final List<String> additionalClientArgs) {

        Map<String, String> updatedTokens = configureTokens(runConfig, mapModClassesToGradle(project, runConfig));

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

            task.args(getArgsStream(runConfig, updatedTokens, false).toArray());
            task.jvmArgs(runConfig.getJvmArgs());
            if (runConfig.isClient()) {
                task.jvmArgs(additionalClientArgs);
            }
            runConfig.getEnvironment().forEach((key,value) -> task.environment(key, runConfig.replace(updatedTokens, value)));
            runConfig.getProperties().forEach((key,value) -> task.systemProperty(key, runConfig.replace(updatedTokens, value)));

            runConfig.getAllSources().stream().map(SourceSet::getRuntimeClasspath).forEach(task::classpath);

            // Stop after this run task so it doesn't try to execute the run tasks, and their dependencies, of sub projects
            if (runConfig.getForceExit()) {
                task.doLast(t -> System.exit(0)); // TODO: Find better way to stop gracefully
            }
        });
    }

    // Workaround for the issue where file paths with spaces are improperly split into multiple args.
    protected static String fixupArg(String replace)
    {
        if (replace.startsWith("\""))
            return replace;

        if (!replace.contains(" "))
            return replace;

        return '"' + replace + '"';
    }

    protected static String getArgs(RunConfig runConfig, Map<String, String> updatedTokens)
    {
        return getArgsStream(runConfig, updatedTokens, true).collect(Collectors.joining(" "));
    }

    protected static Stream<String> getArgsStream(RunConfig runConfig, Map<String, String> updatedTokens, boolean wrapSpaces)
    {
        Stream<String> args = runConfig.getArgs().stream().map((value) -> runConfig.replace(updatedTokens, value));
        return wrapSpaces ? args.map(RunConfigGenerator::fixupArg) : args;
    }

    protected static String getJvmArgs(@Nonnull RunConfig runConfig, List<String> additionalClientArgs, Map<String, String> updatedTokens)
    {
        return getJvmArgsStream(runConfig, additionalClientArgs, updatedTokens)
                .collect(Collectors.joining(" "));
    }

    private static Stream<String> getJvmArgsStream(@Nonnull RunConfig runConfig, List<String> additionalClientArgs, Map<String, String> updatedTokens)
    {
        final Stream<String> propStream = Stream.concat(
                runConfig.getProperties().entrySet().stream()
                    .map(kv -> String.format("-D%s=%s", kv.getKey(), runConfig.replace(updatedTokens, kv.getValue()))),
                runConfig.getJvmArgs().stream()).map(RunConfigGenerator::fixupArg);
        if (runConfig.isClient()) {
            return Stream.concat(propStream, additionalClientArgs.stream());
        }
        return propStream;
    }

    static abstract class XMLConfigurationBuilder extends RunConfigGenerator {

        @Nonnull
        protected abstract Map<String, Document> createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder documentBuilder, List<String> additionalClientArgs);

        @Override
        public final void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project, List<String> additionalClientArgs) {
            try {
                final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                final Transformer transformer = transformerFactory.newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                minecraft.getRuns().forEach(runConfig -> {
                    final Map<String, Document> documents = createRunConfiguration(project, runConfig, docBuilder, additionalClientArgs);

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
        protected abstract JsonObject createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, List<String> additionalClientArgs);

        @Override
        public final void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project, List<String> additionalClientArgs) {
            final JsonObject rootObject = new JsonObject();
            rootObject.addProperty("version", "0.2.0");
            JsonArray runConfigs = new JsonArray();
            minecraft.getRuns().forEach(runConfig -> {
                runConfigs.add(createRunConfiguration(project, runConfig, additionalClientArgs));
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

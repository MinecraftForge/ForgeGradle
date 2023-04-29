/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import com.google.common.base.Suppliers;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

public abstract class RunConfigGenerator
{
    public abstract void createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final File runConfigurationsDir, @Nonnull final Project project, List<String> additionalClientArgs);

    public static void createIDEGenRunsTasks(@Nonnull final MinecraftExtension minecraft, @Nonnull final TaskProvider<Task> prepareRuns, @Nonnull final TaskProvider<Task> makeSourceDirs, List<String> additionalClientArgs) {
        final Project project = minecraft.getProject();

        final Map<String, Triple<List<Object>, File, Supplier<RunConfigGenerator>>> ideConfigurationGenerators = ImmutableMap.<String, Triple<List<Object>, File, Supplier<RunConfigGenerator>>>builder()
                .put("genIntellijRuns", ImmutableTriple.of(Collections.singletonList(prepareRuns),
                        new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"),
                        () -> new IntellijRunGenerator(project.getRootProject())))
                .put("genEclipseRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns, makeSourceDirs),
                        project.getProjectDir(),
                        EclipseRunGenerator::new))
                .put("genVSCodeRuns", ImmutableTriple.of(ImmutableList.of(prepareRuns, makeSourceDirs),
                        new File(project.getProjectDir(), ".vscode"),
                        VSCodeRunGenerator::new))
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
                    configurationGenerator.getRight().get().createRunConfiguration(minecraft, runConfigurationsDir, project, additionalClientArgs);
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

    protected static void elementAttribute(@Nonnull Document document, @Nonnull final Element parent, @Nonnull final String attributeType, @Nonnull final String key, @Nonnull final Object value) {
        final Element attribute = document.createElement(attributeType + "Attribute");
        {
            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value.toString());
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
            final SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

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

    /**
     * @deprecated Use {@link #configureTokensLazy(Project, RunConfig, Stream)}
     */
    @Deprecated
    // TODO: remove this when we can break compat
    protected static Map<String, String> configureTokens(final Project project, @Nonnull RunConfig runConfig, Stream<String> modClasses) {
        project.getLogger().warn("WARNING: RunConfigGenerator#configureTokens was called instead of the lazy variant, configureTokensLazy.");
        project.getLogger().warn("This means longer evaluation times as all tokens are evaluated. Please use configureTokensLazy.");
        return configureTokensLazy(project, runConfig, modClasses)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
    }

    protected static Map<String, Supplier<String>> configureTokensLazy(final Project project, @Nonnull RunConfig runConfig, Stream<String> modClasses) {
        Map<String, Supplier<String>> tokens = new HashMap<>();
        runConfig.getTokens().forEach((k, v) -> tokens.put(k, () -> v));
        runConfig.getLazyTokens().forEach((k, v) -> tokens.put(k, Suppliers.memoize(v::get)));
        tokens.compute("source_roots", (key, sourceRoots) -> Suppliers.memoize(() -> ((sourceRoots != null)
                ? Stream.concat(Arrays.stream(sourceRoots.get().split(File.pathSeparator)), modClasses)
                : modClasses).distinct().collect(Collectors.joining(File.pathSeparator))));
        BiFunction<Supplier<String>, String, String> classpathJoiner = (supplier, evaluated) -> {
            if (supplier == null)
                return evaluated;
            String oldCp = supplier.get();
            return oldCp == null || oldCp.isEmpty() ? evaluated : String.join(File.pathSeparator, oldCp, evaluated);
        };
        // Can't lazily evaluate these as they create tasks we have to do in the current context
        String runtimeClasspath = classpathJoiner.apply(tokens.get("runtime_classpath"), createRuntimeClassPathList(project));
        tokens.put("runtime_classpath", () -> runtimeClasspath);
        String minecraftClasspath = classpathJoiner.apply(tokens.get("minecraft_classpath"), createMinecraftClassPath(project));
        tokens.put("minecraft_classpath", () -> minecraftClasspath);

        File classpathFolder = new File(project.getBuildDir(), "classpath");
        BinaryOperator<String> classpathFileWriter = (filename, classpath) -> {
            if (!classpathFolder.isDirectory() && !classpathFolder.mkdirs())
                throw new IllegalStateException("Could not create directory at " + classpathFolder.getAbsolutePath());
            File outputFile = new File(classpathFolder, runConfig.getUniqueFileName() + "_" + filename + ".txt");
            try (Writer classpathWriter = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
                IOUtils.write(String.join(System.lineSeparator(), classpath.split(File.pathSeparator)), classpathWriter);
            } catch (IOException e) {
                project.getLogger().error("Exception when writing classpath to file {}", outputFile, e);
            }
            return outputFile.getAbsolutePath();
        };
        tokens.put("runtime_classpath_file",
                Suppliers.memoize(() -> classpathFileWriter.apply("runtimeClasspath", runtimeClasspath)));
        tokens.put("minecraft_classpath_file",
                Suppliers.memoize(() -> classpathFileWriter.apply("minecraftClasspath", minecraftClasspath)));

        // *Grumbles about having to keep a workaround for a "dummy" hack that should have never existed*
        runConfig.getEnvironment().compute("MOD_CLASSES", (key,value) ->
                Strings.isNullOrEmpty(value) || "dummy".equals(value) ? "{source_roots}" : value);

        return tokens;
    }

    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final TaskProvider<Task> prepareRuns, final List<String> additionalClientArgs) {
        return createRunTask(runConfig, project, prepareRuns.get(), additionalClientArgs);
    }

    private static String getResolvedClasspath(Configuration toResolve) {
        return toResolve.copyRecursive().resolve().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));
    }

    protected static String createRuntimeClassPathList(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration runtimeClasspath = configurations.getByName("runtimeClasspath");
        return getResolvedClasspath(runtimeClasspath);
    }

    protected static String createMinecraftClassPath(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration minecraft = configurations.findByName("minecraft");
        if (minecraft == null)
            minecraft = configurations.findByName("minecraftImplementation");
        if (minecraft == null)
            throw new IllegalStateException("Could not find valid minecraft configuration!");
        return getResolvedClasspath(minecraft);
    }

    public static TaskProvider<JavaExec> createRunTask(final RunConfig runConfig, final Project project, final Task prepareRuns, final List<String> additionalClientArgs) {

        Map<String, Supplier<String>> updatedTokens = configureTokensLazy(project, runConfig, mapModClassesToGradle(project, runConfig));

        TaskProvider<Task> prepareRun = project.getTasks().register("prepare" + Utils.capitalize(runConfig.getTaskName()), Task.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(prepareRuns);

            File workDir = new File(runConfig.getWorkingDirectory());

            if (!workDir.exists()) {
                workDir.mkdirs();
            }
        });

        TaskProvider<Task> prepareRunCompile = project.getTasks().register("prepare" + Utils.capitalize(runConfig.getTaskName()) + "Compile", Task.class,
                task -> task.dependsOn(runConfig.getAllSources().stream().map(SourceSet::getClassesTaskName).toArray()));

        return project.getTasks().register(runConfig.getTaskName(), JavaExec.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(prepareRun, prepareRunCompile);

            File workDir = new File(runConfig.getWorkingDirectory());

            if (!workDir.exists()) {
                if (!workDir.mkdirs()) {
                    throw new IllegalArgumentException("Could not create configuration directory " + workDir);
                }
            }

            task.setWorkingDir(workDir);
            task.getMainClass().set(runConfig.getMain());
            JavaToolchainService service = project.getExtensions().getByType(JavaToolchainService.class);
            task.getJavaLauncher().convention(service.launcherFor(project.getExtensions().getByType(JavaPluginExtension.class).getToolchain()));

            task.args(getArgsStream(runConfig, updatedTokens, false).toArray());
            runConfig.getJvmArgs().forEach((arg) -> task.jvmArgs(runConfig.replace(updatedTokens, arg)));
            if (runConfig.isClient()) {
                additionalClientArgs.forEach((arg) -> task.jvmArgs(runConfig.replace(updatedTokens, arg)));
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

    protected static String getArgs(RunConfig runConfig, Map<String, ?> updatedTokens)
    {
        return getArgsStream(runConfig, updatedTokens, true).collect(Collectors.joining(" "));
    }

    protected static Stream<String> getArgsStream(RunConfig runConfig, Map<String, ?> updatedTokens, boolean wrapSpaces)
    {
        Stream<String> args = runConfig.getArgs().stream().map((value) -> runConfig.replace(updatedTokens, value));
        return wrapSpaces ? args.map(RunConfigGenerator::fixupArg) : args;
    }

    protected static String getJvmArgs(@Nonnull RunConfig runConfig, List<String> additionalClientArgs, Map<String, ?> updatedTokens)
    {
        return getJvmArgsStream(runConfig, additionalClientArgs, updatedTokens)
                .collect(Collectors.joining(" "));
    }

    private static Stream<String> getJvmArgsStream(@Nonnull RunConfig runConfig, List<String> additionalClientArgs, Map<String, ?> updatedTokens)
    {
        final Stream<String> propStream = Stream.concat(
                runConfig.getProperties().entrySet().stream()
                    .map(kv -> String.format("-D%s=%s", kv.getKey(), runConfig.replace(updatedTokens, kv.getValue()))),
                runConfig.getJvmArgs().stream().map(value -> runConfig.replace(updatedTokens, value))).map(RunConfigGenerator::fixupArg);
        if (runConfig.isClient()) {
            return Stream.concat(propStream, additionalClientArgs.stream());
        }
        return propStream;
    }

    static abstract class XMLConfigurationBuilder extends RunConfigGenerator {

        @Nonnull
        protected abstract Map<String, Document> createRunConfiguration(@Nonnull final MinecraftExtension minecraft, @Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder documentBuilder, List<String> additionalClientArgs);

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
                    final Map<String, Document> documents = createRunConfiguration(minecraft, project, runConfig, docBuilder, additionalClientArgs);

                    documents.forEach((fileName, document) -> {
                        final DOMSource source = new DOMSource(document);
                        final File location = new File(runConfigurationsDir, fileName);
                        if (!location.getParentFile().exists())
                            location.getParentFile().mkdirs();
                        final StreamResult result = new StreamResult(location);

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

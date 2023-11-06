/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import com.google.common.base.Suppliers;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.RunConfig;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

public abstract class RunConfigGenerator {
    public abstract void createRunConfiguration(final MinecraftExtension minecraft, final File runConfigurationsDir, final Project project,
            List<String> additionalClientArgs, FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts);

    public static void createIDEGenRunsTasks(final MinecraftExtension minecraft, final TaskProvider<Task> prepareRuns, List<String> additionalClientArgs) {
        final Project project = minecraft.getProject();

        final Map<String, GeneratorData> ideConfigurationGenerators =
                ImmutableMap.<String, GeneratorData>builder()
                .put("genIntellijRuns", new GeneratorData(new File(project.getRootProject().getRootDir(), ".idea/runConfigurations"),
                        () -> new IntellijRunGenerator(project.getRootProject())))
                .put("genEclipseRuns", new GeneratorData(project.getProjectDir(),
                        EclipseRunGenerator::new))
                .put("genVSCodeRuns", new GeneratorData(new File(project.getProjectDir(), ".vscode"),
                        VSCodeRunGenerator::new))
                .build();

        ideConfigurationGenerators.forEach((taskName, configurationGenerator) -> {
            project.getTasks().register(taskName, GenIDERunsTask.class, task -> {
                task.dependsOn(prepareRuns);

                task.getRunConfigurationsFolder().set(configurationGenerator.outputFolder);
                task.getRunConfigGenerator().set(project.provider(configurationGenerator.generatorFactory::get));
                task.getMinecraftExtension().set(minecraft);
                task.getAdditionalClientArgs().set(additionalClientArgs);
                task.getMinecraftArtifacts().from(getMinecraftArtifacts(project));
                task.getRuntimeClasspathArtifacts().from(getRuntimeClasspathArtifacts(project));
            });
        });
    }

    protected static void elementOption(Document document, final Element parent, final String name, final String value) {
        final Element option = document.createElement("option");

        option.setAttribute("name", name);
        option.setAttribute("value", value);

        parent.appendChild(option);
    }

    protected static void elementAttribute(Document document, final Element parent, final String attributeType, final String key, final Object value) {
        final Element attribute = document.createElement(attributeType + "Attribute");

        attribute.setAttribute("key", key);
        attribute.setAttribute("value", value.toString());
        parent.appendChild(attribute);
    }

    @Nullable
    protected static String replaceRootDirBy(final Project project, @Nullable String value, final String replacement) {
        if (value == null || value.isEmpty())
            return value;

        return value.replace(project.getRootDir().toString(), replacement);
    }

    protected static Stream<String> mapModClassesToGradle(Project project, RunConfig runConfig) {
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

    protected static Map<String, Supplier<String>> configureTokensLazy(final Project project, RunConfig runConfig, Stream<String> modClasses,
            FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts) {
        Map<String, Supplier<String>> tokens = new HashMap<>();
        runConfig.getTokens().forEach((k, v) -> tokens.put(k, () -> v));
        runConfig.getLazyTokens().forEach((k, v) -> tokens.put(k, Suppliers.memoize(v::get)));
        tokens.compute("source_roots", (key, sourceRoots) -> Suppliers.memoize(() -> ((sourceRoots != null)
                ? Stream.concat(Arrays.stream(sourceRoots.get().split(File.pathSeparator)), modClasses)
                : modClasses).distinct().collect(Collectors.joining(File.pathSeparator))));

        Supplier<String> runtimeClasspath = tokens.compute("runtime_classpath", makeClasspathToken(runtimeClasspathArtifacts));
        Supplier<String> minecraftClasspath = tokens.compute("minecraft_classpath", makeClasspathToken(minecraftArtifacts));

        File classpathFolder = new File(project.getLayout().getBuildDirectory().getAsFile().get(), "classpath");
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
                Suppliers.memoize(() -> classpathFileWriter.apply("runtimeClasspath", runtimeClasspath.get())));
        tokens.put("minecraft_classpath_file",
                Suppliers.memoize(() -> classpathFileWriter.apply("minecraftClasspath", minecraftClasspath.get())));

        // *Grumbles about having to keep a workaround for a "dummy" hack that should have never existed*
        runConfig.getEnvironment().compute("MOD_CLASSES", (key, value) ->
                Strings.isNullOrEmpty(value) || "dummy".equals(value) ? "{source_roots}" : value);

        return tokens;
    }

    private static BiFunction<String, Supplier<String>, Supplier<String>> makeClasspathToken(FileCollection classpath) {
        return (key, supplier) -> Suppliers.memoize(() -> {
            String resolvedClasspath = getResolvedClasspath(classpath.getFiles());
            if (supplier == null) return resolvedClasspath;
            String oldCp = supplier.get();
            if (Strings.isNullOrEmpty(oldCp)) return resolvedClasspath;
            if (Strings.isNullOrEmpty(resolvedClasspath)) return oldCp;
            return String.join(File.pathSeparator, oldCp, resolvedClasspath);
        });
    }

    @Nonnull
    private static String getResolvedClasspath(Set<File> artifacts) {
        return artifacts.stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator));
    }

    private static FileCollection getArtifactFiles(Configuration runtimeClasspath) {
        return runtimeClasspath.copyRecursive().getIncoming().getArtifacts().getArtifactFiles();
    }

    protected static FileCollection getMinecraftArtifacts(final Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        Configuration minecraft = configurations.findByName("minecraft");
        if (minecraft == null)
            minecraft = configurations.findByName("minecraftImplementation");
        if (minecraft == null)
            throw new IllegalStateException("Could not find valid minecraft configuration!");

        return getArtifactFiles(minecraft);
    }

    protected static FileCollection getRuntimeClasspathArtifacts(final Project project) {
        return getArtifactFiles(project.getConfigurations().getByName("runtimeClasspath"));
    }

    public static TaskProvider<MinecraftRunTask> createRunTask(final RunConfig runConfig, final Project project, final TaskProvider<Task> prepareRuns, final List<String> additionalClientArgs) {
        TaskProvider<MinecraftPrepareRunTask> prepareRun = project.getTasks().register(runConfig.getPrepareTaskName(), MinecraftPrepareRunTask.class, task ->
            task.dependsOn(prepareRuns));

        TaskProvider<MinecraftPrepareRunTask> prepareRunCompile = project.getTasks().register(runConfig.getPrepareCompileTaskName(), MinecraftPrepareRunTask.class,
                task -> task.dependsOn(runConfig.getAllSources().stream().map(SourceSet::getClassesTaskName).toArray()));

        return project.getTasks().register(runConfig.getTaskName(), MinecraftRunTask.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(prepareRun, prepareRunCompile);
            // The actual run task should always build sources regardless of if prepareRunXXX does
            task.dependsOn(runConfig.getAllSources().stream().map(SourceSet::getClassesTaskName).toArray());

            task.getRunConfig().set(runConfig);
            task.getMainClass().set(runConfig.getMain());
            task.getAdditionalClientArgs().set(additionalClientArgs);
            task.getMinecraftArtifacts().from(getMinecraftArtifacts(project));
            task.getRuntimeClasspathArtifacts().from(getRuntimeClasspathArtifacts(project));
        });
    }

    // Workaround for the issue where file paths with spaces are improperly split into multiple args.
    protected static String fixupArg(String replace) {
        if (replace.startsWith("\""))
            return replace;

        if (!replace.contains(" "))
            return replace;

        return '"' + replace + '"';
    }

    protected static String getArgs(RunConfig runConfig, Map<String, ?> updatedTokens) {
        return getArgsStream(runConfig, updatedTokens, true).collect(Collectors.joining(" "));
    }

    protected static Stream<String> getArgsStream(RunConfig runConfig, Map<String, ?> updatedTokens, boolean wrapSpaces) {
        Stream<String> args = runConfig.getArgs().stream().map((value) -> runConfig.replace(updatedTokens, value));
        return wrapSpaces ? args.map(RunConfigGenerator::fixupArg) : args;
    }

    protected static String getJvmArgs(RunConfig runConfig, List<String> additionalClientArgs, Map<String, ?> updatedTokens) {
        return getJvmArgsStream(runConfig, additionalClientArgs, updatedTokens)
                .collect(Collectors.joining(" "));
    }

    private static Stream<String> getJvmArgsStream(RunConfig runConfig, List<String> additionalClientArgs, Map<String, ?> updatedTokens) {
        final Stream<String> propStream = Stream.concat(
                runConfig.getProperties().entrySet().stream()
                        .map(kv -> String.format("-D%s=%s", kv.getKey(), runConfig.replace(updatedTokens, kv.getValue()))),
                runConfig.getJvmArgs().stream().map(value -> runConfig.replace(updatedTokens, value))).map(RunConfigGenerator::fixupArg);
        if (runConfig.isClient()) {
            return Stream.concat(propStream, additionalClientArgs.stream());
        }
        return propStream;
    }

    abstract static class XMLConfigurationBuilder extends RunConfigGenerator {
        protected abstract Map<String, Document> createRunConfiguration(final MinecraftExtension minecraft, final Project project, final RunConfig runConfig, final DocumentBuilder documentBuilder,
                List<String> additionalClientArgs, FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts);

        @Override
        public final void createRunConfiguration(final MinecraftExtension minecraft, final File runConfigurationsDir, final Project project, List<String> additionalClientArgs,
                FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts) {
            try {
                final DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                final Transformer transformer = transformerFactory.newTransformer();

                transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                minecraft.getRuns().forEach(runConfig -> {
                    MinecraftRunTask.prepareWorkingDirectory(runConfig);

                    final Map<String, Document> documents = createRunConfiguration(minecraft, project, runConfig, docBuilder, additionalClientArgs, minecraftArtifacts, runtimeClasspathArtifacts);

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

    abstract static class JsonConfigurationBuilder extends RunConfigGenerator {
        protected abstract JsonObject createRunConfiguration(final Project project, final RunConfig runConfig, List<String> additionalClientArgs,
                FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts);

        @Override
        public final void createRunConfiguration(final MinecraftExtension minecraft, final File runConfigurationsDir, final Project project,
                List<String> additionalClientArgs, FileCollection minecraftArtifacts, FileCollection runtimeClasspathArtifacts) {
            final JsonObject rootObject = new JsonObject();
            rootObject.addProperty("version", "0.2.0");
            JsonArray runConfigs = new JsonArray();
            minecraft.getRuns().forEach(runConfig -> {
                MinecraftRunTask.prepareWorkingDirectory(runConfig);

                runConfigs.add(createRunConfiguration(project, runConfig, additionalClientArgs, minecraftArtifacts, runtimeClasspathArtifacts));
            });
            rootObject.add("configurations", runConfigs);

            try (Writer writer = new FileWriter(new File(runConfigurationsDir, "launch.json"))) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                writer.write(gson.toJson(rootObject));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected static class GeneratorData {
        protected final File outputFolder;
        protected final Supplier<RunConfigGenerator> generatorFactory;

        public GeneratorData(File outputFolder, Supplier<RunConfigGenerator> generatorFactory) {
            this.outputFolder = outputFolder;
            this.generatorFactory = generatorFactory;
        }
    }
}

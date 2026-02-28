/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import com.google.gson.reflect.TypeToken;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.work.DisableCachingByDefault;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

// This is mostly taken from ForgeGradle 6 but slimmed down to what we need
@DisableCachingByDefault(because = "ForgeGradle would require more information to cache this task")
abstract class SlimeLauncherEclipseConfiguration extends DefaultTask implements ForgeGradleTask, SlimeLauncherRunTask {
    static TaskProvider<SlimeLauncherEclipseConfiguration> register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, MinecraftDependencyInternal mcdep, String runTaskName) {
        var metadata = mcdep.getMetadataTask();
        var generateEclipseRunTaskName = sourceSet.getTaskName("genEclipseRun", options.getName()) + "For" + Util.dependencyToCamelCase(mcdep.getModule());

        var genEclipseRun = project.getTasks().register(generateEclipseRunTaskName, SlimeLauncherEclipseConfiguration.class, task -> {
            task.getRunName().set(options.getName());
            task.setDescription("Generates the '%s' Slime Launcher run configuration for Eclipse.".formatted(options.getName()));
            task.getOutputFile().set(task.getProjectLayout().getProjectDirectory().file(runTaskName + ".launch"));

            var configName = sourceSet.getRuntimeClasspathConfigurationName();
            var config = project.getConfigurations().getByName(configName);
            task.getProjectDependencies().addAll(config.getIncoming().getArtifacts().getResolvedArtifacts()
                .map(artifacts -> {
                    var ret = new ArrayList<String>();
                    // We need to reference our self as well
                    ret.add(getProjectEclipseName(project));

                    var root = project.getRootProject();
                    for (var artifact : artifacts) {
                        var id = artifact.getId().getComponentIdentifier();
                        if (id instanceof ProjectComponentIdentifier projectIdentifier) {
                            var dep = root.project(projectIdentifier.getProjectPath());
                            ret.add(getProjectEclipseName(dep));
                        }
                    }
                    return ret;
                }));

            var runtimeClasspath = task.getObjects().fileCollection().from(
                task.getProviders().provider(() -> {
                    var runtime = sourceSet.getRuntimeClasspath();
                    var eclipseModel = project.getExtensions().findByType(EclipseModel.class);
                    if (eclipseModel == null)
                        return runtime.getFiles();

                    // We need to build a map of sourcesets to real output paths like Eclipse's plugin does.
                    // There is no known exposure of this stuff, so have to do it ourselves.
                    // https://github.com/gradle/gradle/blob/master/platforms/ide/ide/src/main/java/org/gradle/plugins/ide/eclipse/model/internal/SourceFoldersCreator.java#L220
                    var classpath = eclipseModel.getClasspath();
                    var sortedSourceSets = sortSourceSets(classpath.getSourceSets());
                    var replacements = new HashMap<File, File>();
                    var base = classpath.getBaseSourceOutputDir().getAsFile().get();
                    var claimed = new HashSet<File>();
                    claimed.add(classpath.getDefaultOutputDir());

                    // Gather the output name eclipse will use, and all outputs gradle expects
                    for (var sources : sortedSourceSets) {
                        var name =  sources.getName();
                        var path = new File(base, name);
                        while (claimed.contains(path)) {
                            name += '_';
                            path = new File(base, name);
                        }
                        claimed.add(path);
                        if (sources.getOutput().getResourcesDir() != null)
                            replacements.put(sources.getOutput().getResourcesDir(), path);
                        for (var dir : sources.getOutput().getClassesDirs().getFiles())
                            replacements.put(dir, path);
                    }

                    // Now replace the existing classpath with the ones eclipse will use
                    var ret = new LinkedHashSet<File>(runtime.getFiles().size());
                    for (var file : runtime.getFiles())
                        ret.add(replacements.getOrDefault(file, file));
                    return ret;
                }));
            SlimeLauncherRunHelper.configure(task, mcdep, runtimeClasspath);
            task.getSourceSetName().set(sourceSet.getName());

            task.getCacheDir().set(task.getObjects().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s".formatted(mcdep.getPath())).map(task.problems.ensureFileLocation())));
            task.getLocalCacheDir().set(task.getObjects().directoryProperty().value(task.localCaches().dir("slime-launcher/cache/%s".formatted(mcdep.getPath())).map(task.problems.ensureFileLocation())));
            task.getMetadata().setFrom(metadata.map(SlimeLauncherMetadata::getMetadata));
            task.getRunsJson().set(metadata.flatMap(SlimeLauncherMetadata::getRunsJson));

            task.getOptions().set(options);
        });

        project.getTasks().named("genEclipseRuns", task -> task.dependsOn(genEclipseRun));
        return genEclipseRun;
    }


    protected abstract @OutputFile RegularFileProperty getOutputFile();

    protected abstract @Input Property<String> getProjectName();

    public abstract @Input @Override Property<String> getSourceSetName();

    protected abstract @Input @Optional Property<String> getEclipseProjectName();

    protected abstract @Input Property<String> getRunName();

    protected abstract @Nested Property<JavaLauncher> getJavaLauncher();
    protected abstract @Input ListProperty<String> getProjectDependencies();

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getClasspath();

    protected abstract @Input Property<String> getMainClass();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    protected abstract @Internal DirectoryProperty getCacheDir();
    public abstract @Internal @Override DirectoryProperty getLocalCacheDir();
    public abstract @InputFiles @Override ConfigurableFileCollection getMetadata();
    public abstract @InputFiles @Override ConfigurableFileCollection getMinecraftClasspath();
    public abstract @InputFiles @Override ConfigurableFileCollection getRuntimeClasspath();
    public abstract @InputFiles @Override ConfigurableFileCollection getPatcherModules();
    public abstract @Input @Override Property<String> getMinecraftVersion();
    public abstract @Input @Override Property<String> getMCPVersion();
    public abstract @Input @Override Property<String> getMappingChannel();
    public abstract @Input @Override Property<String> getMappingVersion();
    //protected abstract @InputFile @Override RegularFileProperty getSrgToMcp();

    protected abstract @InputFile @Optional RegularFileProperty getRunsJson();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject WorkerExecutor getWorkerExecutor();

    final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    @Inject
    public SlimeLauncherEclipseConfiguration() {
        this.getProjectName().convention(this.getProject().getName());
        this.getEclipseProjectName().convention(getProject().provider(() -> getProjectEclipseName(this.getProject())));

        var tool = this.getTool(Tools.SLIMELAUNCHER);
        this.getClasspath().from(tool.getClasspath());
        this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(Util.launcherFor(getProject(),tool.getJavaVersion()));
    }

    @TaskAction
    protected void exec() {
        if (!this.getEclipseProjectName().isPresent())
            problems.reportMissingEclipsePlugin(this.getName());

        DirectoryProperty workingDir;

        //region Launcher Metadata Inheritance
        Map<String, RunConfig> configs = Map.of();
        var jsons = this.getRunsJson().getAsFile().getOrNull();
        if (jsons != null && jsons.exists())
            configs = JsonData.fromJson(jsons, new TypeToken<>() { });

        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(configs, this.getSourceSetName().get());
        var tokens = SlimeLauncherRunHelper.buildTokens(this);
        var unknown = new HashSet<String>();

        var args = new ArrayList<>(List.of(
            "--main", options.getMainClass().get(),
            "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
            "--metadata", this.getMetadata().getSingleFile().getAbsolutePath(),
            "--"
        ));
        for (var arg : options.getArgs().getOrElse(List.of()))
            args.add(Util.replaceTokens(tokens, arg, unknown));

        var jvmArgs = new ArrayList<String>();
        for (var arg : options.getJvmArgs().getOrElse(List.of()))
            jvmArgs.add(Util.replaceTokens(tokens, arg, unknown));
        if (!options.getClasspath().isEmpty())
            this.getClasspath().setFrom(options.getClasspath());
        if (options.getMinHeapSize().filter(Util::isPresent).isPresent())
            jvmArgs.add("-Xms" + options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util::isPresent).isPresent())
            jvmArgs.add("-Xmx" + options.getMaxHeapSize().get());
        for (var property : options.getSystemProperties().getOrElse(Map.of()).entrySet())
            jvmArgs.add("-D" + property.getKey() + '=' + Util.replaceTokens(tokens, property.getValue(), unknown));

        var env = new HashMap<String, String>();
        for (var entry : options.getEnvironment().get().entrySet()) {
            var value = Util.replaceTokens(tokens, entry.getValue(), unknown);
            env.put(entry.getKey(), value);
        }

        workingDir = options.getWorkingDir();
        //endregion

        for (var token : unknown)
            getLogger().debug("Unknown Run Token: {}", token);

        //region Slime Launcher setup
        try {
            Files.createDirectories(workingDir.get().getAsFile().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //endregion

        var queue = this.getWorkerExecutor().classLoaderIsolation();

        queue.submit(Action.class, parameters -> {
            parameters.getOutputFile().set(this.getOutputFile());
            parameters.getEclipseProjectName().set(this.getEclipseProjectName().orElse(this.getProjectName()));
            parameters.getProjectDependencies().set(this.getProjectDependencies());
            parameters.getClasspath().setFrom(this.getClasspath());
            parameters.getMainClass().set(this.getMainClass().get());
            parameters.getArgs().set(args);
            parameters.getJvmArgs().set(jvmArgs);
            parameters.getWorkingDir().set(workingDir);
            parameters.getEnvironment().set(env);
            parameters.getJavaHome().set(this.getJavaLauncher().map(j -> j.getMetadata().getInstallationPath()));
            parameters.getJavaVersion().set(this.getJavaLauncher().map(j -> j.getMetadata().getLanguageVersion().toString()));
        });
    }

    private static List<SourceSet> sortSourceSets(@Nullable Iterable<SourceSet> sourceSets) {
        if (sourceSets == null)
            return new ArrayList<>(0);
        var ret = new ArrayList<SourceSet>();
        for (var item : sourceSets)
            ret.add(item);
        ret.sort(Comparator.comparing(SlimeLauncherEclipseConfiguration::toComparable));
        return ret;
    }

    private static Integer toComparable(SourceSet sourceSet) {
        String name = sourceSet.getName();
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(name)) {
            return 0;
        } else if (SourceSet.TEST_SOURCE_SET_NAME.equals(name)) {
            return 1;
        } else {
            return 2;
        }
    }

    static abstract class Action implements WorkAction<Action.Parameters> {
        interface Parameters extends WorkParameters {
            RegularFileProperty getOutputFile();

            Property<String> getEclipseProjectName();
            ListProperty<String> getProjectDependencies();

            ConfigurableFileCollection getClasspath();

            Property<String> getMainClass();

            ListProperty<String> getArgs();

            ListProperty<String> getJvmArgs();

            DirectoryProperty getWorkingDir();

            DirectoryProperty getJavaHome();
            Property<String> getJavaVersion();

            MapProperty<String, String> getEnvironment();
        }

        @Inject
        public Action() { }

        @Override
        public void execute() {
            var parameters = getParameters();

            DocumentBuilder documentBuilder;
            Transformer transformer;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                transformer = TransformerFactory.newInstance().newTransformer();
            } catch (ParserConfigurationException | TransformerConfigurationException e) {
                throw new RuntimeException(e);
            }

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            var launch = documentBuilder.newDocument();
            var rootElement = launch.createElement("launchConfiguration");

            rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROJECT_ATTR", parameters.getEclipseProjectName().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.MAIN_TYPE", parameters.getMainClass().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.VM_ARGUMENTS", String.join(" ", parameters.getJvmArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", String.join(" ", parameters.getArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.WORKING_DIRECTORY", parameters.getWorkingDir().getAsFile().get().getAbsolutePath());
            //stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.JRE_CONTAINER", parameters.getJavaHome().getAsFile().get().getAbsolutePath());
            mapAttribute(launch, rootElement, "org.eclipse.debug.core.environmentVariables", parameters.getEnvironment().get());
            var classpathList = classpathList(rootElement);
            addClasspathProjects(classpathList, parameters.getProjectDependencies());
            addClasspathLibraries(classpathList, parameters.getClasspath());
            addClasspathJava(classpathList, parameters.getJavaVersion().get());
            booleanAttribute(launch, rootElement, "org.eclipse.jdt.launching.DEFAULT_CLASSPATH", false);

            launch.appendChild(rootElement);

            var source = new DOMSource(launch);
            var result = new StreamResult(parameters.getOutputFile().getAsFile().get());

            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }

        private static void stringAttribute(Document document, Element parent, String key, Object value) {
            var attribute = document.createElement("stringAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value.toString());
            parent.appendChild(attribute);
        }

        private static void booleanAttribute(Document document, Element parent, String key, boolean value) {
            var attribute = document.createElement("booleanAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", Boolean.toString(value));
            parent.appendChild(attribute);
        }

        private static void listAttribute(Document document, Element parent, String key, Iterable<?> list) {
            var attribute = document.createElement("listAttribute");
            attribute.setAttribute("key", key);

            for (var v : list) {
                var listEntry = document.createElement("listEntry");
                listEntry.setAttribute("value", v.toString());
                attribute.appendChild(listEntry);
            }
            parent.appendChild(attribute);
        }

        private static final String CLASSPATH_ENTRY_PREFIX = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><runtimeClasspathEntry ";
        private static final String CLASSPATH_ENTRY_SUFFIX = " path=\"5\" />";

        private static Element addChild(Element parent, String name) {
            var ret = parent.getOwnerDocument().createElement(name);
            parent.appendChild(ret);
            return ret;
        }

        private static Element classpathList(Element parent) {
            var attribute = addChild(parent, "listAttribute");
            attribute.setAttribute("key", "org.eclipse.jdt.launching.CLASSPATH");
            return attribute;
        }

        private static void classpathEntry(Element parent, int type, String value) {
            addChild(parent, "listEntry").setAttribute("value", CLASSPATH_ENTRY_PREFIX + value + " type=\"" + type + "\"" + CLASSPATH_ENTRY_SUFFIX);
        }

        private static void addClasspathLibraries(Element parent, FileCollection files) {
            for (var v : files.getFiles())
                classpathEntry(parent, 2, "externalArchive=\"" + v + "\"");
        }

        private static void addClasspathProjects(Element parent, ListProperty<String> projects) {
            for (var v : projects.get()) {
                classpathEntry(parent, 1, "projectName=\"" + v + "\"");
                classpathEntry(parent, 4, "containerPath=\"org.eclipse.buildship.core.gradleclasspathcontainer\" javaProject=\"" + v + "\"");
            }
        }

        private static void addClasspathJava(Element parent, String version) {
            classpathEntry(parent, 4, "containerPath=\"org.eclipse.jdt.launching.JRE_CONTAINER/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType/JavaSE-" + version + "\"");
        }

        private static void mapAttribute(Document document, Element parent, String key, Map<String, ?> map) {
            var attribute = document.createElement("mapAttribute");
            attribute.setAttribute("key", key);

            for (var entry : map.entrySet()) {
                var k = entry.getKey();
                var v = entry.getValue();

                var mapEntry = document.createElement("mapEntry");
                mapEntry.setAttribute("key", k);
                mapEntry.setAttribute("value", v.toString());
                attribute.appendChild(mapEntry);
            }
            parent.appendChild(attribute);
        }
    }

    private static String getProjectEclipseName(Project project) {
        var eclipse = project.getExtensions().findByType(EclipseModel.class);
        var name = eclipse == null ? null : eclipse.getProject().getName();
        return name != null ? name : project.getName();
    }
}

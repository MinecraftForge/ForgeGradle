/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
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
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
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
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// This is mostly taken from ForgeGradle 6 but slimmed down to what we need
abstract class SlimeLauncherEclipseConfiguration extends DefaultTask implements ForgeGradleTask {
    protected abstract @OutputFile RegularFileProperty getOutputFile();

    protected abstract @Input Property<String> getProjectName();

    protected abstract @Input @Optional Property<String> getEclipseProjectName();

    protected abstract @Input Property<String> getRunName();

    protected abstract @Nested Property<JavaLauncher> getJavaLauncher();

    protected abstract @InputFiles @Classpath ConfigurableFileCollection getClasspath();

    protected abstract @Input Property<String> getMainClass();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    protected abstract @Internal DirectoryProperty getCacheDir();

    protected abstract @InputFile RegularFileProperty getMetadataZip();

    protected abstract @InputFile RegularFileProperty getRunsJson();

    protected abstract @Inject ObjectFactory getObjects();

    protected abstract @Inject ProviderFactory getProviders();

    protected abstract @Inject ProjectLayout getProjectLayout();

    protected abstract @Inject WorkerExecutor getWorkerExecutor();

    final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    @Inject
    public SlimeLauncherEclipseConfiguration() {
        this.getProjectName().convention(this.getProject().getName());
        this.getEclipseProjectName().convention(getProviders().provider(() -> {
            var eclipse = getProject().getExtensions().findByType(EclipseModel.class);
            return eclipse == null ? null : eclipse.getProject().getName();
        }));

        var tool = this.getTool(Tools.SLIMELAUNCHER);
        this.getClasspath().from(tool.getClasspath());
        this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(Util.launcherFor(getProject(),tool.getJavaVersion()));
    }

    @TaskAction
    protected void exec() {
        List<String> args;
        List<String> jvmArgs;
        MapProperty<String, String> environment;
        DirectoryProperty workingDir;

        //region Launcher Metadata Inheritance
        Map<String, RunConfig> configs = Map.of();
        try {
            configs = JsonData.fromJson(
                this.getRunsJson().getAsFile().get(),
                new TypeToken<>() { }
            );
        } catch (JsonIOException e) {
            // continue
        }

        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(configs);

        args = new ArrayList<>(options.getArgs().getOrElse(List.of()));
        jvmArgs = new ArrayList<>(options.getJvmArgs().getOrElse(List.of()));
        if (!options.getClasspath().isEmpty())
            this.getClasspath().setFrom(options.getClasspath());
        if (options.getMinHeapSize().filter(Util::isPresent).isPresent())
            jvmArgs.add("-Xms" + options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util::isPresent).isPresent())
            jvmArgs.add("-Xmx" + options.getMaxHeapSize().get());
        for (var property : options.getSystemProperties().getOrElse(Map.of()).entrySet())
            jvmArgs.add("-D" + property.getKey() + '=' + property.getValue());
        environment = options.getEnvironment();
        workingDir = options.getWorkingDir();
        //endregion

        //region Slime Launcher setup
        args.addAll(List.of("--main", options.getMainClass().get(),
            "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
            "--metadata", this.getMetadataZip().get().getAsFile().getAbsolutePath(),
            "--"));

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
            parameters.getMainClass().set(this.getMainClass().get());
            parameters.getArgs().set(args);
            parameters.getJvmArgs().set(jvmArgs);
            parameters.getWorkingDir().set(workingDir);
            parameters.getEnvironment().set(environment);
            parameters.getJavaHome().set(this.getJavaLauncher().map(j -> j.getMetadata().getInstallationPath()));
        });
    }

    static abstract class Action implements WorkAction<Action.Parameters> {
        interface Parameters extends WorkParameters {
            RegularFileProperty getOutputFile();

            Property<String> getEclipseProjectName();

            Property<String> getMainClass();

            ListProperty<String> getArgs();

            ListProperty<String> getJvmArgs();

            DirectoryProperty getWorkingDir();

            DirectoryProperty getJavaHome();

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
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.JRE_CONTAINER", parameters.getJavaHome().getAsFile().get().getAbsolutePath());
            mapAttribute(launch, rootElement, "org.eclipse.debug.core.environmentVariables", parameters.getEnvironment().get());

            var source = new DOMSource(launch);
            var result = new StreamResult(parameters.getOutputFile().getAsFile().get());

            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }

        private static void stringAttribute(Document document, Element parent, String key, String value) {
            var attribute = document.createElement("stringAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value);
            parent.appendChild(attribute);
        }

        private static void mapAttribute(Document document, Element parent, String key, Map<String, String> map) {
            var attribute = document.createElement("mapAttribute");
            attribute.setAttribute("key", key);

            for (var entry : map.entrySet()) {
                var k = entry.getKey();
                var v = entry.getValue();

                var mapEntry = document.createElement("mapEntry");
                mapEntry.setAttribute("key", k);
                mapEntry.setAttribute("value", v);
                attribute.appendChild(mapEntry);
            }
            parent.appendChild(attribute);
        }
    }
}

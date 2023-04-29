/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.gradle.common.tasks.ide.CopyEclipseResources;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.RunConfig;

import net.minecraftforge.gradle.common.util.Utils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EclipseRunGenerator extends RunConfigGenerator.XMLConfigurationBuilder
{
    @Override
    @Nonnull
    protected Map<String, Document> createRunConfiguration(@Nonnull final MinecraftExtension mc, @Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder documentBuilder, List<String> additionalClientArgs) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        Map<String, Supplier<String>> updatedTokens = configureTokensLazy(project, runConfig, mapModClassesToEclipse(project, runConfig));

        final File workingDirectory = new File(runConfig.getWorkingDirectory());

        // Eclipse requires working directory to exist
        if (!workingDirectory.exists()) {
            workingDirectory.mkdirs();
        }

        // Java run config
        final Document javaDocument = documentBuilder.newDocument();
        {
            final Element rootElement = javaDocument.createElement("launchConfiguration");
            {
                rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");

                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROJECT_ATTR", getEclipseProjectName(project));
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.MAIN_TYPE", runConfig.getMain());
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.VM_ARGUMENTS",
                        getJvmArgs(runConfig, additionalClientArgs, updatedTokens));
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS",
                        getArgs(runConfig, updatedTokens));
                elementAttribute(javaDocument, rootElement, "string", "org.eclipse.jdt.launching.WORKING_DIRECTORY", runConfig.getWorkingDirectory());

                final Element envs = javaDocument.createElement("mapAttribute");
                {
                    envs.setAttribute("key", "org.eclipse.debug.core.environmentVariables");

                    runConfig.getEnvironment().forEach((name, value) -> {
                        final Element envEntry = javaDocument.createElement("mapEntry");
                        {
                            envEntry.setAttribute("key", name);
                            envEntry.setAttribute("value", runConfig.replace(updatedTokens, value));
                        }
                        envs.appendChild(envEntry);
                    });
                }
                rootElement.appendChild(envs);
            }
            javaDocument.appendChild(rootElement);
        }

        final String configName = (mc.getGenerateRunFolders().get() ? runConfig.getFolderName() + " - " : "") + runConfig.getTaskName() + ".launch";
        final boolean copyResources = mc.getCopyIdeResources().get();

        if (copyResources || mc.getEnableEclipsePrepareRuns().get()) {
            final String launchConfigName = project.getName() + " - " + runConfig.getTaskName() + "Slim";
            documents.put(".eclipse/configurations/" + launchConfigName + ".launch", javaDocument);

            // Create a gradle document
            final Document gradleDocument = documentBuilder.newDocument();
            {
                final Element rootElement = gradleDocument.createElement("launchConfiguration");
                {
                    rootElement.setAttribute("type", "org.eclipse.buildship.core.launch.runconfiguration");

                    elementAttribute(gradleDocument, rootElement, "string", "gradle_distribution", "GRADLE_DISTRIBUTION(WRAPPER)");
                    elementAttribute(gradleDocument, rootElement, "boolean", "offline_mode", project.getGradle().getStartParameter().isOffline());
                    elementAttribute(gradleDocument, rootElement, "boolean", "show_console_view", "true");
                    elementAttribute(gradleDocument, rootElement, "boolean", "show_execution_view", "true");
                    elementAttribute(gradleDocument, rootElement, "string", "working_dir", project.getRootDir());
                }

                final Element tasks = gradleDocument.createElement("listAttribute");
                tasks.setAttribute("key", "tasks");
                {
                    final Element taskEntry = gradleDocument.createElement("listEntry");
                    taskEntry.setAttribute("value", project.getTasks().getByName("prepare" + Utils.capitalize(runConfig.getTaskName())).getPath());
                    tasks.appendChild(taskEntry);

                    if (copyResources) {
                        final Task copyTask = project.getTasks().findByName(CopyEclipseResources.NAME);
                        if (copyTask != null) {
                            final Element copyIde = gradleDocument.createElement("listEntry");
                            copyIde.setAttribute("value", copyTask.getPath());
                            tasks.appendChild(copyIde);
                        }
                    }
                }
                rootElement.appendChild(tasks);
                gradleDocument.appendChild(rootElement);
            }
            final String gradleConfigName = project.getName() + " - prepare" + Utils.capitalize(runConfig.getTaskName());
            documents.put(".eclipse/configurations/" + gradleConfigName + ".launch", gradleDocument);

            final Document groupDocument = documentBuilder.newDocument();
            {
                final Element rootElement = groupDocument.createElement("launchConfiguration");
                rootElement.setAttribute("type", "org.eclipse.debug.core.groups.GroupLaunchConfigurationType");
                final List<SubTaskConfiguration> configurations = ImmutableList.of(
                        new SubTaskConfiguration(gradleConfigName, SubTaskConfiguration.Mode.RUN),
                        new SubTaskConfiguration(launchConfigName, SubTaskConfiguration.Mode.INHERIT)
                );
                for (int i = 0; i < configurations.size(); i++) {
                    final SubTaskConfiguration config = configurations.get(i);
                    elementAttribute(groupDocument, rootElement, "string", "org.eclipse.debug.core.launchGroup." + i + ".action", "NONE");
                    elementAttribute(groupDocument, rootElement, "boolean", "org.eclipse.debug.core.launchGroup." + i + ".adoptIfRunning", false);
                    elementAttribute(groupDocument, rootElement, "boolean", "org.eclipse.debug.core.launchGroup." + i + ".enabled", true);
                    elementAttribute(groupDocument, rootElement, "string", "org.eclipse.debug.core.launchGroup." + i + ".mode", config.mode);
                    elementAttribute(groupDocument, rootElement, "string", "org.eclipse.debug.core.launchGroup." + i + ".name", config.name);
                }
                groupDocument.appendChild(rootElement);
            }
            documents.put(configName, groupDocument);
        } else {
            documents.put(configName, javaDocument);
        }

        return documents;
    }

    static String getEclipseProjectName(@Nonnull final Project project) {
        final EclipseModel eclipse = project.getExtensions().findByType(EclipseModel.class);
        return eclipse == null ? project.getName() : eclipse.getProject().getName();
    }

    static Stream<String> mapModClassesToEclipse(@Nonnull final Project project, @Nonnull final RunConfig runConfig) {
        final Map<SourceSet, Map<String, String>> sourceSetsToOutputs = new IdentityHashMap<>();

        project.getRootProject().getAllprojects().forEach(proj -> {
            final EclipseModel eclipse = proj.getExtensions().findByType(EclipseModel.class);
            if (eclipse == null) return;
            final EclipseClasspath classpath = eclipse.getClasspath();

            final Map<String, String> outputs = classpath.resolveDependencies().stream()
                    .filter(SourceFolder.class::isInstance)
                    .map(SourceFolder.class::cast)
                    .map(SourceFolder::getOutput)
                    .distinct()
                    .collect(Collectors.toMap(output -> output.split("/")[output.split("/").length - 1], output -> proj.file(output).getAbsolutePath()));

            final JavaPluginExtension javaPlugin = proj.getExtensions().findByType(JavaPluginExtension.class);
            if (javaPlugin != null)
            {
                for (SourceSet sourceSet : javaPlugin.getSourceSets())
                {
                    sourceSetsToOutputs.computeIfAbsent(sourceSet, a -> new HashMap<>()).putAll(outputs);
                }
            }
        });

        if (runConfig.getMods().isEmpty())
        {
            return runConfig.getAllSources().stream()
                    .map(sourceSet -> sourceSetsToOutputs.getOrDefault(sourceSet, Collections.emptyMap()).get(sourceSet.getName()))
                    .filter(Objects::nonNull)
                    .map(s -> String.join(File.pathSeparator, s, s)); // <resources>:<classes>
        } else
        {
            final SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

            return runConfig.getMods().stream()
                    .flatMap(modConfig -> {
                        return (modConfig.getSources().isEmpty() ? Stream.of(main) : modConfig.getSources().stream())
                                .map(sourceSet -> sourceSetsToOutputs.getOrDefault(sourceSet, Collections.emptyMap()).get(sourceSet.getName()))
                                .filter(Objects::nonNull)
                                .map(output -> modConfig.getName() + "%%" + output)
                                .map(s -> String.join(File.pathSeparator, s, s)); // <resources>:<classes>
                    });
        }
    }

    private static final class SubTaskConfiguration {
        public final String name;
        public final Mode mode;

        private SubTaskConfiguration(String name, Mode mode) {
            this.name = name;
            this.mode = mode;
        }

        private enum Mode {
            PROFILE,
            INHERIT,
            DEBUG,
            RUN,
            COVERAGE;

            @Override
            public String toString() {
                return super.toString().toLowerCase(Locale.ROOT);
            }
        }
    }
}

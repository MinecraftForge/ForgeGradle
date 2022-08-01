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

import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class IntellijRunGenerator extends RunConfigGenerator.XMLConfigurationBuilder
{
    boolean useGradlePaths = true;

    public IntellijRunGenerator(@Nonnull final Project project)
    {
        detectGradleDelegation(project);
    }

    private void detectGradleDelegation(Project project)
    {
        XPath xpath = XPathFactory.newInstance().newXPath();

        // This file contains the current gradle import settings.
        File ideaGradleSettings = project.file(".idea/gradle.xml");
        if (ideaGradleSettings.exists() && ideaGradleSettings.isFile())
        {
            try (InputStream in = Files.newInputStream(ideaGradleSettings.toPath()))
            {
                Node value = (Node) xpath.evaluate(
                        "/project/component[@name='GradleSettings']/option[@name='linkedExternalProjectsSettings']/GradleProjectSettings/option[@name='delegatedBuild']/@value",
                        new InputSource(in),
                        XPathConstants.NODE);
                if (value != null)
                {
                    useGradlePaths = Boolean.parseBoolean(value.getTextContent());
                    return;
                }
            }
            catch (IOException | XPathExpressionException e)
            {
                e.printStackTrace();
            }
        }

        // This value is normally true, and won't be used unless the project's gradle.xml is missing.
        File ideaWorkspace = project.file(".idea/workspace.xml");
        if (ideaWorkspace.exists() && ideaWorkspace.isFile())
        {
            try (InputStream in = Files.newInputStream(ideaWorkspace.toPath()))
            {
                Node value = (Node) xpath.evaluate(
                        "/project/component[@name='DefaultGradleProjectSettings']/option[@name='delegatedBuild']/@value",
                        new InputSource(in),
                        XPathConstants.NODE);
                if (value != null)
                {
                    useGradlePaths = Boolean.parseBoolean(value.getTextContent());
                    return;
                }
            }
            catch (IOException | XPathExpressionException e)
            {
                e.printStackTrace();
            }
        }

        // Fallback, in case someone has a file-based project instead of a directory-based project.
        final IdeaModel idea = project.getExtensions().findByType(IdeaModel.class);
        File ideaFileProject = project.file(idea != null ? idea.getProject().getOutputFile() : (project.getName() + ".ipr"));
        if (ideaFileProject.exists() && ideaFileProject.isFile())
        {
            try (InputStream in = Files.newInputStream(ideaFileProject.toPath()))
            {
                Node value = (Node) xpath.evaluate(
                        "/project/component[@name='GradleSettings']/option[@name='linkedExternalProjectsSettings']/GradleProjectSettings/option[@name='delegatedBuild']/@value",
                        new InputSource(in),
                        XPathConstants.NODE);
                if (value != null)
                {
                    useGradlePaths = Boolean.parseBoolean(value.getTextContent());
                }
            }
            catch (IOException | XPathExpressionException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    @Nonnull
    protected Map<String, Document> createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final DocumentBuilder documentBuilder, List<String> additionalClientArgs) {
        final Map<String, Document> documents = new LinkedHashMap<>();

        Map<String, Supplier<String>> updatedTokens = configureTokensLazy(project, runConfig,
                useGradlePaths
                    ? mapModClassesToGradle(project, runConfig)
                    : mapModClassesToIdea(project, runConfig)
        );

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
                    elementOption(javaDocument, configuration, "VM_PARAMETERS",
                            getJvmArgs(runConfig, additionalClientArgs, updatedTokens));
                    elementOption(javaDocument, configuration, "PROGRAM_PARAMETERS",
                            getArgs(runConfig, updatedTokens));
                    elementOption(javaDocument, configuration, "WORKING_DIRECTORY",
                            replaceRootDirBy(project, runConfig.getWorkingDirectory(), "$PROJECT_DIR$"));

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
                                envEntry.setAttribute("value", replaceRootDirBy(project, runConfig.replace(updatedTokens, value), "$PROJECT_DIR$"));
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
                            makeTask.setAttribute("name", runConfig.getBuildAllProjects() ? "MakeProject" : "Make");
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

    private static Stream<String> mapModClassesToIdea(@Nonnull final Project project, @Nonnull final RunConfig runConfig) {
        final Map<SourceSet, Project> sourceSetsToProjects = new IdentityHashMap<>();

        project.getRootProject().getAllprojects().forEach(proj -> {
            final JavaPluginExtension javaPlugin = proj.getExtensions().findByType(JavaPluginExtension.class);
            if (javaPlugin != null) {
                for (SourceSet sourceSet : javaPlugin.getSourceSets()) {
                    sourceSetsToProjects.put(sourceSet, proj);
                }
            }
        });

        if (runConfig.getMods().isEmpty()) {
            final IdeaModel idea = project.getExtensions().findByType(IdeaModel.class);
            return getIdeaPathsForSourceset(project, idea, "production", null);
        } else {

            return runConfig.getMods().stream()
                    .flatMap(modConfig -> modConfig.getSources().stream()
                            .flatMap(source -> {
                                String outName = source.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : source.getName();
                                final Project sourceSetProject = sourceSetsToProjects.getOrDefault(source, project);
                                final IdeaModel ideaModel = sourceSetProject.getExtensions().findByType(IdeaModel.class);
                                return getIdeaPathsForSourceset(sourceSetProject, ideaModel, outName, modConfig.getName());
                            }));
        }
    }

    private static Stream<String> getIdeaPathsForSourceset(@Nonnull Project project, @Nullable IdeaModel idea, String outName, @Nullable String modName)
    {
        String ideaResources, ideaClasses;
        try
        {
            String outputPath = idea != null
                    ? idea.getModule().getPathFactory().path("$MODULE_DIR$").getCanonicalUrl()
                    : project.getProjectDir().getCanonicalPath();

            ideaResources = Paths.get(outputPath, "out", outName, "resources").toFile().getCanonicalPath();
            ideaClasses = Paths.get(outputPath, "out", outName, "classes").toFile().getCanonicalPath();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Error getting paths for idea run configs", e);
        }

        if (modName != null)
        {
            ideaResources = modName + "%%" + ideaResources;
            ideaClasses = modName + "%%" + ideaClasses;
        }

        return Stream.of(ideaResources, ideaClasses);
    }
}

/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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
package net.minecraftforge.gradle.patcher;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.patcher.PatcherConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.*;
import net.minecraftforge.gradle.util.CopyInto;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.json.version.Library;
import net.minecraftforge.gradle.util.json.version.Version;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;

public class PatcherPlugin extends BasePlugin<PatcherExtension>
{
    @Override
    public void applyPlugin()
    {
        // create and add the namedDomainObjectContainer to the extension object

        NamedDomainObjectContainer<PatcherProject> container = project.container(PatcherProject.class, new PatcherProjectFactory(this));
        getExtension().setProjectContainer(container);
        container.whenObjectAdded(new Action<PatcherProject>() {
            @Override
            public void execute(PatcherProject arg0)
            {
                createProject(arg0);
            }

        });
        container.whenObjectRemoved(new Action<PatcherProject>() {
            @Override
            public void execute(PatcherProject arg0)
            {
                removeProject(arg0);
            }

        });

        // top level tasks
        {
            Task task = makeTask(TASK_SETUP);
            task.setGroup(GROUP_FG);
            task.setDescription("Sets up all the projects complete with run configurations for both Eclipse and Intellij");

            Delete cleanTask = maybeMakeTask(TASK_CLEAN, Delete.class);
            cleanTask.delete(getExtension().getDelayedWorkspaceDir());
            cleanTask.delete(project.getBuildDir());
            cleanTask.setGroup(GROUP_FG);
            cleanTask.setDescription("Completely cleans the workspace for a fresh build. Deletes the 'build' folder and the specified workspaceDir");

            task = makeTask(TASK_GEN_PATCHES);
            task.setGroup(GROUP_FG);
            task.setDescription("Generates patches for all the configured projects. (requires that setup was run before hand)");

            task = maybeMakeTask(TASK_BUILD);
            task.setGroup(GROUP_FG);
            task.setDescription("Builds all output packages. (outputs found in build/distributions)");
        }

        makeGeneralSetupTasks();
        makePackagingTasks();
        makeCleanTasks();
    }

    protected void makeGeneralSetupTasks()
    {
        DeobfuscateJar deobfJar = makeTask(TASK_DEOBF, DeobfuscateJar.class);
        {
            deobfJar.setInJar(delayedFile(Constants.JAR_MERGED));
            deobfJar.setOutJar(delayedFile(JAR_DEOBF));
            deobfJar.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfJar.setExceptorCfg(delayedFile(EXC_SRG));
            deobfJar.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfJar.setApplyMarkers(true);
            deobfJar.setDoesCache(false);
            // access transformers are added afterEvaluate
            deobfJar.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }

        ApplyFernFlowerTask decompileJar = makeTask(TASK_DECOMP, ApplyFernFlowerTask.class);
        {
            decompileJar.setInJar(delayedFile(JAR_DEOBF));
            decompileJar.setOutJar(delayedFile(JAR_DECOMP));
            decompileJar.setDoesCache(false);
            decompileJar.setClasspath(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPS));
            decompileJar.dependsOn(deobfJar);
        }

        PostDecompileTask postDecompileJar = makeTask(TASK_POST_DECOMP, PostDecompileTask.class);
        {
            postDecompileJar.setInJar(delayedFile(JAR_DECOMP));
            postDecompileJar.setOutJar(delayedFile(JAR_DECOMP_POST));
            postDecompileJar.setPatches(delayedFile(MCP_PATCHES_MERGED));
            postDecompileJar.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecompileJar.setDoesCache(false);
            postDecompileJar.dependsOn(decompileJar);
        }

        TaskGenSubprojects createProjects = makeTask(TASK_GEN_PROJECTS, TaskGenSubprojects.class);
        {
            createProjects.setWorkspaceDir(getExtension().getDelayedWorkspaceDir());
            createProjects.addRepo("minecraft", Constants.URL_LIBRARY);
            createProjects.putProject("Clean", null, null, null, null);
            createProjects.setJavaLevel("1.6");
        }

        Task setupProjects = makeTask(TASK_SETUP_PROJECTS);
        setupProjects.dependsOn(createProjects);

        TaskSubprojectCall makeIdeProjects = makeTask(TASK_GEN_IDES, TaskSubprojectCall.class);
        {
            makeIdeProjects.setProjectDir(getExtension().getDelayedWorkspaceDir());
            makeIdeProjects.setCallLine("cleanEclipse cleanIdea eclipse idea");
            makeIdeProjects.dependsOn(setupProjects);
        }
    }

    protected void makePackagingTasks()
    {
        // for universal

        TaskReobfuscate obf = makeTask(TASK_REOBFUSCATE, TaskReobfuscate.class);
        {
            obf.setSrg(delayedFile(SRG_MCP_TO_NOTCH));
            obf.setExc(delayedFile(EXC_MCP));
            obf.setPreFFJar(delayedFile(JAR_DEOBF));
            obf.setMethodsCsv(delayedFile(CSV_METHOD));
            obf.setFieldsCsv(delayedFile(CSV_FIELD));
            obf.setOutJar(delayedFile(JAR_OBFUSCATED));
            obf.addLibs(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPS));
            obf.dependsOn(TASK_GENERATE_SRGS, TASK_SETUP_PROJECTS);
        }

        TaskGenBinPatches genBinPatches = makeTask(TASK_GEN_BIN_PATCHES, TaskGenBinPatches.class);
        {
            genBinPatches.setCleanClient(delayedFile(JAR_CLIENT_FRESH));
            genBinPatches.setCleanServer(delayedFile(JAR_SERVER_FRESH));
            genBinPatches.setCleanMerged(delayedFile(JAR_MERGED));
            genBinPatches.setDirtyJar(delayedFile(JAR_OBFUSCATED));
            genBinPatches.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            genBinPatches.setRuntimeBinPatches(delayedFile(BINPATCH_RUN));
            genBinPatches.setDevBinPatches(delayedFile(BINPATCH_DEV));
            genBinPatches.dependsOn(obf);
        }

        TaskExtractNew extractObfClasses = makeTask(TASK_EXTRACT_OBF_CLASSES, TaskExtractNew.class);
        {
            // why not merged? it contains the SideOnly and stuff that we want in the classes
            extractObfClasses.addCleanSource(delayedFile(JAR_CLIENT_FRESH));
            extractObfClasses.addCleanSource(delayedFile(JAR_SERVER_FRESH));
            extractObfClasses.addDirtySource(delayedFile(JAR_OBFUSCATED));
            extractObfClasses.setOutput(delayedFile(JAR_OBF_CLASSES));
            extractObfClasses.setEnding(".class");
            extractObfClasses.dependsOn(obf);
        }

        TaskCompressLZMA compressDeobf = makeTask("compressDeobf", TaskCompressLZMA.class);
        {
            compressDeobf.setInputFile(delayedFile(MCP_DATA_SRG)); // SRG_NOTCH_TO_SRG but doesnt require genSrgs task
            compressDeobf.setOutputFile(delayedFile(DEOBF_DATA));
            compressDeobf.dependsOn(TASK_EXTRACT_MCP);
        }

        TaskProcessJson procJson = makeTask(TASK_PROCESS_JSON, TaskProcessJson.class);
        {
            procJson.setInstallerJson(delayedFile(JSON_INSTALLER));
            procJson.setUniversalJson(delayedFile(JSON_UNIVERSAL));
            procJson.getOutputs().upToDateWhen(Constants.CALL_FALSE);
        }

        Jar outputJar = makeTask(TASK_OUTPUT_JAR, Jar.class);
        {
            outputJar.from(delayedTree(JAR_OBF_CLASSES));
            outputJar.from(delayedFile(BINPATCH_RUN));
            outputJar.from(delayedFile(DEOBF_DATA));
            outputJar.from(delayedFile(JSON_UNIVERSAL));
            outputJar.setBaseName(project.getName());
            outputJar.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            outputJar.getOutputs().upToDateWhen(Constants.CALL_FALSE); // rebuild every time.
            outputJar.setDestinationDir(new File(DIR_OUTPUT));
            outputJar.dependsOn(genBinPatches, extractObfClasses, compressDeobf, procJson);
        }

        // add to build
        project.getTasks().getByName(TASK_BUILD).dependsOn(outputJar);

        // ------------------------------
        // for installer

        EtagDownloadTask dlInstaller = makeTask("downloadInstaller", EtagDownloadTask.class);
        {
            dlInstaller.setUrl(delayedString(INSTALLER_URL));
            dlInstaller.setDieWithError(true);
            dlInstaller.setFile(delayedFile(JAR_INSTALLER));
        }

        Zip installer = makeTask(TASK_BUILD_INSTALLER, Zip.class);
        {
            installer.from(outputJar);
            installer.from(delayedTree(JAR_INSTALLER), new CopyInto(PatcherPlugin.class, "", "!*.json", "!*.png"));
            installer.from(delayedTree(JSON_INSTALLER));
            installer.setBaseName(project.getName());
            installer.setClassifier("installer");
            installer.setExtension("jar");
            installer.setDestinationDir(new File(DIR_OUTPUT));
            installer.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            installer.getOutputs().upToDateWhen(Constants.CALL_FALSE); // rebuild every time.
            installer.dependsOn(dlInstaller, outputJar, procJson);
        }

        // ------------------------------
        // for userdev

        TaskExtractNew extractNonMcSources = makeTask(TASK_EXTRACT_OBF_SOURCES, TaskExtractNew.class);
        {
            // why not merged? it contains the SideOnly and stuff that we want in the classes
            extractNonMcSources.addCleanSource(delayedFile(JAR_DECOMP_POST));
            extractNonMcSources.setOutput(delayedFile(ZIP_USERDEV_SOURCES));
            extractNonMcSources.setEnding(".java");
            extractNonMcSources.dependsOn(TASK_SETUP_PROJECTS);
        }

        Zip combineRes = makeTask(TASK_COMBINE_RESOURCES, Zip.class);
        {
            File out = delayedFile(ZIP_USERDEV_RES).call();
            combineRes.setDestinationDir(out.getParentFile());
            combineRes.setArchiveName(out.getName());
            combineRes.setIncludeEmptyDirs(false);
            combineRes.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        }

        TaskMergeFiles mergeFiles = makeTask(TASK_MERGE_FILES, TaskMergeFiles.class);
        {
            mergeFiles.setOutSrg(delayedFile(SRG_MERGED_USERDEV));
            mergeFiles.setOutExc(delayedFile(EXC_MERGED_USERDEV));
            mergeFiles.setOutAt(delayedFile(AT_MERGED_USERDEV));
        }

        TaskGenPatches userdevPatches = makeTask(TASK_GEN_PATCHES_USERDEV, TaskGenPatches.class);
        {
            userdevPatches.setPatchDir(delayedFile(DIR_USERDEV_PATCHES));
            userdevPatches.addOriginalSource(delayedFile(JAR_DECOMP_POST)); // add vanilla SRG named source
        }

        Zip packagePatches = makeTask(TASK_PATCHES_USERDEV, Zip.class);
        {
            File out = delayedFile(ZIP_USERDEV_PATCHES).call();
            packagePatches.setDestinationDir(out.getParentFile());
            packagePatches.setArchiveName(out.getName());
            packagePatches.from(delayedFile(DIR_USERDEV_PATCHES));
            packagePatches.dependsOn(userdevPatches);
        }

        Zip userdev = makeTask(TASK_BUILD_USERDEV, Zip.class);
        {
            userdev.from(delayedFile(DIR_USERDEV));
            userdev.from(getExtension().getDelayedVersionJson()); // cant forge that now can we..
            userdev.rename(".+-dev\\.json", "dev.json");
            userdev.setBaseName(project.getName());
            userdev.setClassifier("userdev");
            userdev.setExtension("jar");
            userdev.setDestinationDir(new File(DIR_OUTPUT));
            userdev.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            userdev.getOutputs().upToDateWhen(Constants.CALL_FALSE); // rebuild every time.
            userdev.dependsOn(genBinPatches, extractObfClasses, packagePatches, extractNonMcSources, combineRes, mergeFiles);
        }
    }

    protected void makeCleanTasks()
    {
        RemapSources remapCleanTask = makeTask(TASK_CLEAN_REMAP, RemapSources.class);
        {
            remapCleanTask.setInJar(delayedFile(JAR_DECOMP_POST));
            remapCleanTask.setOutJar(delayedFile(JAR_REMAPPED));
            remapCleanTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapCleanTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapCleanTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapCleanTask.setAddsJavadocs(false);
            remapCleanTask.setDoesCache(false);
            remapCleanTask.dependsOn(TASK_POST_DECOMP);
        }

        Object delayedRemapped = delayedFile(JAR_REMAPPED);

        ExtractTask extractSrc = makeTask(TASK_CLEAN_EXTRACT_SRC, ExtractTask.class);
        {
            extractSrc.from(delayedRemapped);
            extractSrc.into(subWorkspace("Clean" + DIR_EXTRACTED_SRC));
            extractSrc.include("*.java", "**/*.java");
            extractSrc.setDoesCache(false);
            extractSrc.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }

        ExtractTask extractRes = makeTask(TASK_CLEAN_EXTRACT_RES, ExtractTask.class);
        {
            extractRes.from(delayedRemapped);
            extractRes.into(subWorkspace("Clean" + DIR_EXTRACTED_RES));
            extractRes.exclude("*.java", "**/*.java");
            extractRes.setDoesCache(false);
            extractRes.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }

        CreateStartTask makeStart = makeTask(TASK_CLEAN_MAKE_START, CreateStartTask.class);
        {
            for (String resource : GRADLE_START_RESOURCES)
            {
                makeStart.addResource(resource);
            }

            makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
            makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(DIR_ASSETS));
            makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(Constants.DIR_NATIVES));
            makeStart.addReplacement("@@SRGDIR@@", delayedFile(DIR_MCP_MAPPINGS + "/srgs/"));
            makeStart.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(SRG_NOTCH_TO_SRG));
            makeStart.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(SRG_NOTCH_TO_MCP));
            makeStart.addReplacement("@@SRG_SRG_MCP@@", delayedFile(SRG_SRG_TO_MCP));
            makeStart.addReplacement("@@SRG_MCP_SRG@@", delayedFile(SRG_MCP_TO_SRG));
            makeStart.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(SRG_MCP_TO_NOTCH));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_MAPPINGS));
            makeStart.addReplacement("@@BOUNCERCLIENT@@", "net.minecraft.client.main.Main");
            makeStart.addReplacement("@@TWEAKERCLIENT@@", "");
            makeStart.addReplacement("@@BOUNCERSERVER@@", "net.minecraft.server.MinecraftServer");
            makeStart.addReplacement("@@TWEAKERSERVER@@", "");
            makeStart.setStartOut(subWorkspace("Clean" + DIR_EXTRACTED_START));
            makeStart.setDoesCache(false);
            makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS, TASK_EXTRACT_NATIVES);
            makeStart.getOutputs().upToDateWhen(Constants.CALL_FALSE); //TODO: Abrar, Fix this...
        }

        GenEclipseRunTask eclipseClient = makeTask(TASK_CLEAN_RUNE_CLIENT, GenEclipseRunTask.class);
        {
            eclipseClient.setMainClass(GRADLE_START_CLIENT);
            eclipseClient.setProjectName("Clean");
            eclipseClient.setOutputFile(subWorkspace("Clean/Clean Client.launch"));
            eclipseClient.setRunDir(subWorkspace("run"));
            eclipseClient.dependsOn(makeStart, TASK_GEN_IDES);
        }

        GenEclipseRunTask eclipseServer = makeTask(TASK_CLEAN_RUNE_SERVER, GenEclipseRunTask.class);
        {
            eclipseServer.setMainClass(GRADLE_START_SERVER);
            eclipseServer.setProjectName("Clean");
            eclipseServer.setOutputFile(subWorkspace("Clean/Clean Server.launch"));
            eclipseServer.setRunDir(subWorkspace("run"));
            eclipseServer.dependsOn(makeStart, TASK_GEN_IDES);
        }

        TaskGenIdeaRun ideaClient = makeTask(TASK_CLEAN_RUNJ_CLIENT, TaskGenIdeaRun.class);
        {
            ideaClient.setMainClass(GRADLE_START_CLIENT);
            ideaClient.setProjectName("Clean");
            ideaClient.setConfigName("Clean Client");
            ideaClient.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Client.xml"));
            ideaClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaClient.dependsOn(makeStart, TASK_GEN_IDES);
        }

        TaskGenIdeaRun ideaServer = makeTask(TASK_CLEAN_RUNJ_SERVER, TaskGenIdeaRun.class);
        {
            ideaServer.setMainClass(GRADLE_START_SERVER);
            ideaServer.setProjectName("Clean");
            ideaServer.setConfigName("Clean Server");
            ideaServer.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Server.xml"));
            ideaServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaServer.dependsOn(makeStart, TASK_GEN_IDES);
        }

        // add depends
        project.getTasks().getByName(TASK_GEN_IDES).dependsOn(extractSrc, extractRes, makeStart);
        project.getTasks().getByName(TASK_SETUP).dependsOn(eclipseClient, eclipseServer, ideaClient, ideaServer);
    }

    protected void createProject(PatcherProject patcher)
    {
        PatchSourcesTask patch = makeTask(projectString(TASK_PROJECT_PATCH, patcher), PatchSourcesTask.class);
        {
            // inJar is set afterEvaluate depending on the patch order.
            patch.setOutJar(delayedFile(projectString(JAR_PROJECT_PATCHED, patcher)));
            patch.setPatches(patcher.getDelayedPatchDir());
            patch.setDoesCache(false);
            patch.setMaxFuzz(2);
            patch.setFailOnError(false);
            patch.setMakeRejects(true);
            patch.dependsOn(TASK_POST_DECOMP);
        }

        RemapSources remapTask = makeTask(projectString(TASK_PROJECT_REMAP_JAR, patcher), RemapSources.class);
        {
            // inJar is set afterEvaluate depending on the patch order.
            remapTask.setOutJar(delayedFile(projectString(JAR_PROJECT_REMAPPED, patcher)));
            remapTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapTask.setAddsJavadocs(false);
            remapTask.setDoesCache(false);
            remapTask.dependsOn(TASK_POST_DECOMP, TASK_EXTRACT_MAPPINGS);
            // depend on patch task in afterEval
        }

        ((TaskGenSubprojects) project.getTasks().getByName(TASK_GEN_PROJECTS)).putProject(
                patcher.getCapName(),
                patcher.getDelayedSourcesDir(),
                patcher.getDelayedResourcesDir(),
                patcher.getDelayedTestSourcesDir(),
                patcher.getDelayedTestResourcesDir()
                );

        ExtractTask extractSrc = makeTask(projectString(TASK_PROJECT_EXTRACT_SRC, patcher), ExtractTask.class);
        {
            // set from() thing in afterEval
            extractSrc.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));
            extractSrc.include("*.java", "**/*.java");
            extractSrc.setDoesCache(false);
            extractSrc.dependsOn(patch, remapTask, TASK_GEN_PROJECTS);
            // if depends on both remap and patch, itl happen after whichever is second.
        }

        ExtractTask extractRes = makeTask(projectString(TASK_PROJECT_EXTRACT_RES, patcher), ExtractTask.class);
        {
            // set from() thing in afterEval
            extractRes.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_RES));
            extractRes.exclude("*.java", "**/*.java");
            extractRes.setDoesCache(false);
            extractRes.dependsOn(patch, remapTask, TASK_GEN_PROJECTS);
            // if depends on both remap and patch, itl happen after whichever is second.
        }

        Task setupTask = makeTask(projectString(TASK_PROJECT_SETUP, patcher));
        setupTask.dependsOn(extractSrc, extractRes);

        // Run config generation, not necessary unless its actual dev

        CreateStartTask makeStart = makeTask(projectString(TASK_PROJECT_MAKE_START, patcher), CreateStartTask.class);
        {
            for (String resource : GRADLE_START_RESOURCES)
            {
                makeStart.addResource(resource);
            }

            for (String resource : GRADLE_START_FML_RES)
            {
                makeStart.addResource(resource);
            }

            makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
            makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(DIR_ASSETS));
            makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(Constants.DIR_NATIVES));
            makeStart.addReplacement("@@SRGDIR@@", delayedFile(DIR_MCP_MAPPINGS + "/srgs/"));
            makeStart.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(SRG_NOTCH_TO_SRG));
            makeStart.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(SRG_NOTCH_TO_MCP));
            makeStart.addReplacement("@@SRG_SRG_MCP@@", delayedFile(SRG_SRG_TO_MCP));
            makeStart.addReplacement("@@SRG_MCP_SRG@@", delayedFile(SRG_MCP_TO_SRG));
            makeStart.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(SRG_MCP_TO_NOTCH));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_MAPPINGS));
            makeStart.addReplacement("@@BOUNCERCLIENT@@", patcher.getDelayedMainClassClient());
            makeStart.addReplacement("@@TWEAKERCLIENT@@", patcher.getDelayedTweakClassClient());
            makeStart.addReplacement("@@BOUNCERSERVER@@", patcher.getDelayedMainClassServer());
            makeStart.addReplacement("@@TWEAKERSERVER@@", patcher.getDelayedTweakClassServer());
            makeStart.addExtraLine("net.minecraftforge.gradle.GradleForgeHacks.searchCoremods(this);");
            makeStart.setStartOut(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_START));
            makeStart.setDoesCache(false);
            makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS);
            makeStart.getOutputs().upToDateWhen(Constants.CALL_FALSE); //TODO: Abrar, Fix this...
        }

        GenEclipseRunTask eclipseRunClient = makeTask(projectString(TASK_PROJECT_RUNE_CLIENT, patcher), GenEclipseRunTask.class);
        {
            eclipseRunClient.setMainClass(GRADLE_START_CLIENT);
            eclipseRunClient.setArguments(patcher.getDelayedRunArgsClient());
            eclipseRunClient.setProjectName(patcher.getCapName());
            eclipseRunClient.setOutputFile(subWorkspace(patcher.getCapName() + "/" + patcher.getCapName() + " Client.launch"));
            eclipseRunClient.setRunDir(subWorkspace("run"));
            eclipseRunClient.dependsOn(makeStart, TASK_GEN_IDES);
        }

        GenEclipseRunTask eclipseRunServer = makeTask(projectString(TASK_PROJECT_RUNE_SERVER, patcher), GenEclipseRunTask.class);
        {
            eclipseRunServer.setMainClass(GRADLE_START_SERVER);
            eclipseRunServer.setArguments(patcher.getDelayedRunArgsServer());
            eclipseRunServer.setProjectName(patcher.getCapName());
            eclipseRunServer.setOutputFile(subWorkspace(patcher.getCapName() + "/" + patcher.getCapName() + " Server.launch"));
            eclipseRunServer.setRunDir(subWorkspace("run"));
            eclipseRunServer.dependsOn(makeStart, TASK_GEN_IDES);
        }

        TaskGenIdeaRun ideaRunClient = makeTask(projectString(TASK_PROJECT_RUNJ_CLIENT, patcher), TaskGenIdeaRun.class);
        {
            ideaRunClient.setMainClass(GRADLE_START_CLIENT);
            ideaRunClient.setArguments(patcher.getDelayedRunArgsClient());
            ideaRunClient.setProjectName(patcher.getCapName());
            ideaRunClient.setConfigName(patcher.getCapName() + " Client");
            ideaRunClient.setOutputFile(subWorkspace("/.idea/runConfigurations/" + patcher.getCapName() + "Client.xml"));
            ideaRunClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunClient.dependsOn(makeStart, TASK_GEN_IDES);
        }

        TaskGenIdeaRun ideaRunServer = makeTask(projectString(TASK_PROJECT_RUNJ_SERVER, patcher), TaskGenIdeaRun.class);
        {
            ideaRunServer.setMainClass(GRADLE_START_SERVER);
            ideaRunServer.setArguments(patcher.getDelayedRunArgsServer());
            ideaRunServer.setProjectName(patcher.getCapName());
            ideaRunServer.setConfigName(patcher.getCapName() + " Server");
            ideaRunServer.setOutputFile(subWorkspace("/.idea/runConfigurations/" + patcher.getCapName() + "Server.xml"));
            ideaRunServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunServer.dependsOn(makeStart, TASK_GEN_IDES);
        }

        Task setupDevTask = makeTask(projectString(TASK_PROJECT_SETUP_DEV, patcher));
        setupDevTask.dependsOn(setupTask, makeStart, TASK_GEN_IDES);
        setupDevTask.dependsOn(eclipseRunClient, eclipseRunServer, ideaRunClient, ideaRunServer);

        // fixe starts bieng created after the IDE thing
        project.getTasks().getByName(TASK_GEN_IDES).mustRunAfter(makeStart);

        /// TASKS THAT ARN'T PART OF THE SETUP

        TaskSubprojectCall compile = makeTask(projectString(TASK_PROJECT_COMPILE, patcher), TaskSubprojectCall.class);
        {
            compile.setProjectDir(subWorkspace(patcher.getCapName()));
            compile.setCallLine("jar");
            compile.addInitScript(Resources.getResource(TaskSubprojectCall.class, "initscriptJar.gradle"));
            compile.addReplacement("@RECOMP_DIR@", delayedFile(projectString(DIR_PROJECT_CACHE, patcher)));
            compile.addReplacement("@JAR_NAME@", JAR_PROJECT_RECOMPILED.substring(DIR_PROJECT_CACHE.length() + 1));
            compile.mustRunAfter(setupTask);
        }

        TaskExtractExcModifiers extractExc = makeTask(projectString(TASK_PROJECT_GEN_EXC, patcher), TaskExtractExcModifiers.class);
        {
            extractExc.setInJar(delayedFile(projectString(JAR_PROJECT_RECOMPILED, patcher)));
            extractExc.setOutExc(delayedFile(projectString(EXC_PROJECT, patcher)));
            extractExc.dependsOn(compile);
        }

        ExtractS2SRangeTask extractRangemap = makeTask(projectString(TASK_PROJECT_RANGEMAP, patcher), ExtractS2SRangeTask.class);
        {
            extractRangemap.addSource(patcher.getDelayedSourcesDir());
            extractRangemap.addSource(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));
            extractRangemap.setRangeMap(delayedFile(projectString(RANGEMAP_PROJECT, patcher)));
            extractRangemap.addLibs(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPS));
            extractRangemap.addLibs(delayedFile(projectString(JAR_PROJECT_RECOMPILED, patcher)));
            extractRangemap.dependsOn(compile);
        }

        ApplyS2STask retromap = makeTask(projectString(TASK_PROJECT_RETROMAP, patcher), ApplyS2STask.class);
        {
            retromap.addSource(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));
            retromap.setOut(delayedFile(projectString(JAR_PROJECT_RETROMAPPED, patcher)));
            retromap.addSrg(delayedFile(SRG_MCP_TO_SRG));
            retromap.addExc(delayedFile(EXC_MCP));
            retromap.addExc(delayedFile(EXC_SRG)); // just in case
            retromap.setExcModifiers(delayedFile(projectString(EXC_PROJECT, patcher)));
            retromap.setRangeMap(delayedFile(projectString(RANGEMAP_PROJECT, patcher)));
            retromap.dependsOn(TASK_GENERATE_SRGS, extractExc, extractRangemap);
        }

        ApplyS2STask retromapNonMc = makeTask(projectString(TASK_PROJECT_RETRO_NONMC, patcher), ApplyS2STask.class);
        {
            retromapNonMc.addSource(patcher.getDelayedSourcesDir());
            retromapNonMc.setOut(delayedFile(projectString(JAR_PROJECT_RETRO_NONMC, patcher)));
            retromapNonMc.addSrg(delayedFile(SRG_MCP_TO_SRG));
            retromapNonMc.addExc(delayedFile(EXC_MCP));
            retromapNonMc.addExc(delayedFile(EXC_SRG)); // just in case
            retromapNonMc.setExcModifiers(delayedFile(projectString(EXC_PROJECT, patcher)));
            retromapNonMc.setRangeMap(delayedFile(projectString(RANGEMAP_PROJECT, patcher)));
            retromapNonMc.dependsOn(TASK_GENERATE_SRGS, extractExc, extractRangemap);
        }
    }

    protected void removeProject(PatcherProject patcher)
    {
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_REMAP_JAR, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_SRC, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_RES, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_MAKE_START, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RUNE_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RUNE_SERVER, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RUNJ_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RUNJ_SERVER, patcher)));

        ((TaskGenSubprojects) project.getTasks().getByName(TASK_GEN_PROJECTS)).removeProject(patcher.getCapName());

        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_COMPILE, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_GEN_EXC, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RANGEMAP, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RETROMAP, patcher)));
        project.getTasks().remove(project.getTasks().getByName(projectString(TASK_PROJECT_RETRO_NONMC, patcher)));
    }

    public void afterEvaluate()
    {
        super.afterEvaluate();

        // validate files
        {
            File versionJson = getExtension().getVersionJson();
            File workspaceDir = getExtension().getWorkspaceDir();

            if (workspaceDir == null)
            {
                throw new GradleConfigurationException("A workspaceDir must be specified! eg: minecraft { workspaceDir = 'someDir' }");
            }

            if (versionJson == null || !versionJson.exists())
            {
                throw new GradleConfigurationException("The versionJson could not be found! Are you sure its correct?");
            }

            if (((TaskProcessJson) project.getTasks().getByName(TASK_PROCESS_JSON)).isReleaseJsonNull())
            {
                throw new GradleConfigurationException("Release json not confgiured! add this to your buildscript:  "
                        + TASK_PROCESS_JSON + " { releaseJson = 'path/to/release.json' }");
            }

            if (Strings.isNullOrEmpty(getExtension().getInstallerVersion()) && getExtension().isBuildInstaller())
            {
                throw new GradleConfigurationException("You must specify the installerVersion in the minecraft block if you want to build an installer!");
            }
        }

        // use versionJson stuff
        {
            File versionJson = getExtension().getVersionJson();
            Version version = parseAndStoreVersion(versionJson, versionJson.getParentFile(), delayedFile(Constants.DIR_JSONS).call());

            TaskGenSubprojects createProjects = (TaskGenSubprojects) project.getTasks().getByName(TASK_GEN_PROJECTS);
            Set<String> repos = Sets.newHashSet();

            for (Library lib : version.getLibraries())
            {
                if (lib.applies() && lib.extract == null)
                {
                    createProjects.addCompileDep(lib.getArtifactName());

                    // add repo for url if its not the MC repo, not maven central, and not already added
                    String url = lib.getUrl();
                    if (!url.contains("libraries.minecraft.net") && !url.contains("maven.apache.org") && !repos.contains(url))
                    {
                        createProjects.addRepo("jsonRepo" + repos.size(), url);

                        // add it to here too!
                        this.addMavenRepo(project, "jsonRepo" + repos.size(), url);

                        repos.add(url);
                    }
                }
            }
        }

        // enable installer and userdev
        {
            Task build = project.getTasks().getByName(TASK_BUILD);
            if (getExtension().isBuildUserdev())
            {
                build.dependsOn(TASK_BUILD_USERDEV);
            }
            if (getExtension().isBuildInstaller())
            {
                build.dependsOn(TASK_BUILD_INSTALLER);
            }
        }

        List<PatcherProject> patchersList = sortByPatching(getExtension().getProjects());

        // tasks to be configured
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        Task setupProjectTasks = project.getTasks().getByName(TASK_SETUP_PROJECTS);
        Task genPatchesTask = project.getTasks().getByName(TASK_GEN_PATCHES);
        DeobfuscateJar deobfJar = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);
        TaskGenBinPatches binPatches = (TaskGenBinPatches) project.getTasks().getByName(TASK_GEN_BIN_PATCHES);
        Jar outputJar = (Jar) project.getTasks().getByName(TASK_OUTPUT_JAR);
        Zip resourceZip = (Zip) project.getTasks().getByName(TASK_COMBINE_RESOURCES);
        TaskMergeFiles mergeFiles = (TaskMergeFiles) project.getTasks().getByName(TASK_MERGE_FILES);

        List<File> addedExcs = Lists.newArrayListWithCapacity(patchersList.size());
        List<File> addedSrgs = Lists.newArrayListWithCapacity(patchersList.size());

        PatcherProject lastPatcher = null;

        for (PatcherProject patcher : patchersList)
        {
            patcher.validate(); // validate project

            if (patcher.isApplyMcpPatches())
            {
                PatchSourcesTask patch = (PatchSourcesTask) project.getTasks().getByName(projectString(TASK_PROJECT_PATCH, patcher));
                RemapSources remap = (RemapSources) project.getTasks().getByName(projectString(TASK_PROJECT_REMAP_JAR, patcher));

                // configure the patches to happen after remap
                patch.dependsOn(remap);
                patch.setInJar(delayedFile(projectString(JAR_PROJECT_REMAPPED, patcher)));
                // configure patching input and injects
                if (lastPatcher != null)
                {
                    remap.dependsOn(projectString(TASK_PROJECT_REMAP_JAR, lastPatcher));
                    remap.setInJar(delayedFile(projectString(JAR_PROJECT_REMAPPED, lastPatcher)));
                    patch.addInject(lastPatcher.getDelayedSourcesDir());
                    patch.addInject(lastPatcher.getDelayedResourcesDir());
                } else {
                    remap.setInJar(delayedFile(JAR_DECOMP_POST));
                }

                // configure extract tasks to extract patched
                Object patched = delayedFile(projectString(JAR_PROJECT_PATCHED, patcher));
                ((ExtractTask) project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_SRC, patcher))).from(patched);
                ((ExtractTask) project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_RES, patcher))).from(patched);
            }
            else
            {
                PatchSourcesTask patch = (PatchSourcesTask) project.getTasks().getByName(projectString(TASK_PROJECT_PATCH, patcher));
                RemapSources remap = (RemapSources) project.getTasks().getByName(projectString(TASK_PROJECT_REMAP_JAR, patcher));

                // configure the patches to happen AFTER remap
                remap.dependsOn(patch);
                remap.setInJar(delayedFile(projectString(JAR_PROJECT_PATCHED, patcher)));
                // configure patching input and injects
                if (lastPatcher != null)
                {
                    patch.dependsOn(projectString(TASK_PROJECT_PATCH, lastPatcher));
                    patch.setInJar(delayedFile(projectString(JAR_PROJECT_PATCHED, lastPatcher)));
                    patch.addInject(lastPatcher.getDelayedSourcesDir());
                    patch.addInject(lastPatcher.getDelayedResourcesDir());
                } else {
                    patch.setInJar(delayedFile(JAR_DECOMP_POST));
                }

                Object remapped = delayedFile(projectString(JAR_PROJECT_REMAPPED, patcher));
                ((ExtractTask) project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_SRC, patcher))).from(remapped);
                ((ExtractTask) project.getTasks().getByName(projectString(TASK_PROJECT_EXTRACT_RES, patcher))).from(remapped);
            }

            // get EXCs and SRGs for retromapping
            ApplyS2STask retromap = (ApplyS2STask) project.getTasks().getByName(projectString(TASK_PROJECT_RETROMAP, patcher));
            ApplyS2STask retromapNonMc = (ApplyS2STask) project.getTasks().getByName(projectString(TASK_PROJECT_RETRO_NONMC, patcher));

            // add from previous projects
            for (File f : addedExcs)
            {
                retromap.addExc(f);
                retromapNonMc.addExc(f);
            }
            for (File f : addedSrgs)
            {
                retromap.addSrg(f);
                retromapNonMc.addSrg(f);
            }

            // add from this project
            for (File f : project.fileTree(patcher.getResourcesDir()).getFiles())
            {
                if (f.getName().endsWith(".exc"))
                {
                    retromap.addExc(f);
                    retromapNonMc.addExc(f);
                    addedExcs.add(f);
                    mergeFiles.addExc(f);
                }
                else if (f.getName().endsWith(".srg"))
                {
                    retromap.addSrg(f);
                    retromapNonMc.addSrg(f);
                    addedSrgs.add(f);
                    mergeFiles.addSrg(f);
                }
                else if (f.getName().endsWith("_at.cfg"))
                {
                    // Add ATs for deobf in the same run.. why not...
                    deobfJar.addAt(f);
                    mergeFiles.addAt(f);
                }
            }

            // create genPatches task for it.. if necessary
            if (patcher.doesGenPatches())
            {
                TaskGenPatches genPatches = makeTask(projectString(TASK_PROJECT_GEN_PATCHES, patcher), TaskGenPatches.class);
                genPatches.setPatchDir(patcher.getPatchDir());
                genPatches.setOriginalPrefix(patcher.getPatchPrefixOriginal());
                genPatches.setChangedPrefix(patcher.getPatchPrefixChanged());
                //genPatches.getOutputs().upToDateWhen(CALL_FALSE);
                genPatches.setGroup(GROUP_FG);
                genPatches.setDescription("Generates patches for the '" + patcher.getName() + "' project");

                // add to global task
                genPatchesTask.dependsOn(genPatches);

                if (patcher.isGenMcpPatches())
                {
                    genPatches.addChangedSource(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));

                    if ("clean".equals(patcher.getGenPatchesFrom().toLowerCase()))
                    {
                        genPatches.addOriginalSource(delayedFile(JAR_REMAPPED)); // SRG named vanilla..
                    }
                    else
                    {
                        PatcherProject genFrom = getExtension().getProjects().getByName(patcher.getGenPatchesFrom());
                        genPatches.addOriginalSource(delayedFile(projectString(JAR_PROJECT_REMAPPED, patcher)));
                        genPatches.addOriginalSource(genFrom.getDelayedSourcesDir());
                    }
                }
                else
                {
                    genPatches.addChangedSource(delayedFile(projectString(JAR_PROJECT_RETROMAPPED, patcher)));
                    genPatches.dependsOn(projectString(TASK_PROJECT_RETROMAP, patcher));

                    if ("clean".equals(patcher.getGenPatchesFrom().toLowerCase()))
                    {
                        genPatches.addOriginalSource(delayedFile(JAR_DECOMP_POST)); // SRG named vanilla..
                    }
                    else
                    {
                        PatcherProject genFrom = getExtension().getProjects().getByName(patcher.getGenPatchesFrom());
                        genPatches.addOriginalSource(delayedFile(projectString(JAR_PROJECT_RETROMAPPED, genFrom)));
                        genPatches.addOriginalSource(delayedFile(projectString(JAR_PROJECT_RETRO_NONMC, genFrom)));
                        genPatches.dependsOn(projectString(TASK_PROJECT_RETROMAP, genFrom), projectString(TASK_PROJECT_RETRO_NONMC, genFrom));
                    }
                }

            }

            // add patch sets to bin patches
            binPatches.addPatchSet(patcher.getPatchDir());

            // add resources to output
            resourceZip.from(patcher.getDelayedResourcesDir());
            outputJar.from(patcher.getDelayedResourcesDir());

            // add task dependencies
            setupProjectTasks.dependsOn(projectString(TASK_PROJECT_SETUP, patcher));
            setupTask.dependsOn(projectString(TASK_PROJECT_SETUP_DEV, patcher));

            // set last patcher..
            lastPatcher = patcher;
        }

        // ------------------------------
        // ------------------------------
        // PACKAGING

        PatcherProject patcher = patchersList.get(patchersList.size() - 1);

        TaskReobfuscate reobf = (TaskReobfuscate) project.getTasks().getByName(TASK_REOBFUSCATE);
        reobf.setInJar(delayedFile(projectString(JAR_PROJECT_RECOMPILED, patcher)));
        reobf.dependsOn(projectString(TASK_PROJECT_COMPILE, patcher));

        // Why regenerate patches from clean if the built project already has them?
        // to strip the prefixes.. thats why...
        TaskGenPatches userdevPatches = (TaskGenPatches) (project.getTasks().getByName(TASK_GEN_PATCHES_USERDEV));
        userdevPatches.addChangedSource(delayedFile(projectString(JAR_PROJECT_RETROMAPPED, patcher)));
        userdevPatches.dependsOn(TASK_POST_DECOMP, projectString(TASK_PROJECT_RETROMAP, patcher));

        TaskExtractNew userdevSources = (TaskExtractNew) project.getTasks().getByName(TASK_EXTRACT_OBF_SOURCES);
        userdevSources.addDirtySource(delayedFile(projectString(JAR_PROJECT_RETROMAPPED, patcher)));
        userdevSources.addDirtySource(delayedFile(projectString(JAR_PROJECT_RETRO_NONMC, patcher)));
        userdevSources.dependsOn(projectString(TASK_PROJECT_RETROMAP, patcher), projectString(TASK_PROJECT_RETRO_NONMC, patcher));

        // add version to packaging tasks
        outputJar.setVersion(project.getVersion().toString());
        ((Zip)project.getTasks().getByName(TASK_BUILD_USERDEV)).setVersion(project.getVersion().toString());
        ((Zip)project.getTasks().getByName(TASK_BUILD_INSTALLER)).setVersion(project.getVersion().toString());

        // add them to the maven artifatcs
        if (project.getPlugins().hasPlugin("maven"))
        {
            project.getArtifacts().add("archives", outputJar);
            project.getArtifacts().add("archives", project.getTasks().getByName(TASK_BUILD_USERDEV));
            project.getArtifacts().add("archives", project.getTasks().getByName(TASK_BUILD_INSTALLER));
        }
    }

    /**
     * Sorts the project into the list of patches on each other.
     * Throws GradleConfigurationException if the projects cannot be fitted into the list.
     * Doesnt support potential patching loops, but the clean project cant patch anything, so its unlikely to happen.
     * @return list of sorted projects
     */
    private List<PatcherProject> sortByPatching(NamedDomainObjectContainer<PatcherProject> projects)
    {
        // patcher->patched
        BiMap<PatcherProject, PatcherProject> tempMap = HashBiMap.create();

        for (PatcherProject project : projects)
        {
            String patchAfter = project.getPatchAfter();
            PatcherProject toPut;

            if (patchAfter.equals("clean"))
            {
                toPut = null;
            }
            else
            {
                toPut = projects.findByName(patchAfter);

                if (toPut == null)
                    throw new GradleConfigurationException("Project " + patchAfter + " does not exist! You cannot patch after it!");

                if (toPut.isApplyMcpPatches() && !project.isApplyMcpPatches())
                {
                    // its trying to apply SRG patches after a project that does MCP patches??
                    // IMPOSSIBRU!
                    throw new GradleConfigurationException("Project " + patchAfter + " applies SRG named patches, and is attempting to patch after a project that uses MCP named patches! THATS IMPOSSIBRU!");
                }
            }

            try
            {
                tempMap.put(project, toPut);
            }
            catch (IllegalArgumentException e)
            {
                // must exist already.. thus a duplicate value..
                throw new GradleConfigurationException("2 projects cannot patch after the same project '" + toPut == null ? "clean" : toPut.getName() + "'!");
            }
        }

        // now  patched->patcher
        tempMap = tempMap.inverse();

        ArrayList<PatcherProject> list = new ArrayList<PatcherProject>(projects.size());
        PatcherProject key = tempMap.remove(null); // null is clean
        while (key != null)
        {
            list.add(key);
            key = tempMap.remove(key);
        }

        return list;
    }

    private Closure<File> subWorkspace(String path)
    {
        return getExtension().getDelayedSubWorkspaceDir(path);
    }

    private String projectString(String str, PatcherProject project)
    {
        return str.replace("{CAPNAME}", project.getCapName()).replace("{NAME}", project.getName());
    }
}

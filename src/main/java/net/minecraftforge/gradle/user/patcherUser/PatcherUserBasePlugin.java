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
package net.minecraftforge.gradle.user.patcherUser;

import static net.minecraftforge.gradle.common.Constants.DIR_JSONS;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_MERGED;
import static net.minecraftforge.gradle.common.Constants.TASK_DL_VERSION_JSON;
import static net.minecraftforge.gradle.common.Constants.TASK_GENERATE_SRGS;
import static net.minecraftforge.gradle.common.Constants.TASK_MERGE_JARS;
import static net.minecraftforge.gradle.common.Constants.DIR_LOCAL_CACHE;
import static net.minecraftforge.gradle.user.UserConstants.*;
import static net.minecraftforge.gradle.user.patcherUser.PatcherUserConstants.*;

import java.io.File;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import com.google.common.collect.ImmutableMap;

import groovy.lang.Closure;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.ExtractConfigTask;
import net.minecraftforge.gradle.tasks.PatchSourcesTask;
import net.minecraftforge.gradle.tasks.RemapSources;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.UserBasePlugin;

public abstract class PatcherUserBasePlugin<T extends UserBaseExtension> extends UserBasePlugin<T>
{
    @Override
    @SuppressWarnings("serial")
    protected void applyUserPlugin()
    {
        // add the MC setup tasks..
        String global = DIR_API_JAR_BASE + "/" + REPLACE_API_NAME + "%s-" + REPLACE_API_VERSION;
        String local = DIR_LOCAL_CACHE + "/" + REPLACE_API_NAME + "%s-" + REPLACE_API_VERSION + "-PROJECT(" + project.getName() + ")";

        // grab ATs from resource dirs
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        SourceSet main = javaConv.getSourceSets().getByName("main");
        SourceSet api = javaConv.getSourceSets().getByName("api");

        getExtension().atSources(main, api);

        this.makeDecompTasks(global, local, delayedFile(JAR_MERGED), TASK_MERGE_JARS, delayedFile(MCP_PATCHES_MERGED));

        // setup userdev
        {
            project.getConfigurations().maybeCreate(CONFIG_USERDEV);

            ExtractConfigTask extractUserdev = makeTask(TASK_EXTRACT_USERDEV, ExtractConfigTask.class);
            extractUserdev.setDestinationDir(delayedFile(DIR_USERDEV));
            extractUserdev.setConfig(CONFIG_USERDEV);
            extractUserdev.exclude("META-INF/**", "META-INF/**");
            extractUserdev.dependsOn(TASK_DL_VERSION_JSON);

            extractUserdev.doLast(new Closure<Boolean>(PatcherUserBasePlugin.class) // normalizes to linux endings
            {
                @Override
                public Boolean call()
                {
                    parseAndStoreVersion(delayedFile(JSON_USERDEV).call(), delayedFile(DIR_JSONS).call());
                    return true;
                }
            });

            // See afterEvaluate for more config

            project.getTasks().getByName(TASK_GENERATE_SRGS).dependsOn(extractUserdev);
            project.getTasks().getByName(TASK_RECOMPILE).dependsOn(extractUserdev);
            project.getTasks().getByName(TASK_MAKE_START).dependsOn(extractUserdev);
        }

        // setup deobfuscation
        {
            DeobfuscateJar deobfBin = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN);
            DeobfuscateJar deobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);

            deobfBin.addAt(delayedFile(AT_USERDEV));
            deobf.addAt(delayedFile(AT_USERDEV));
        }

        // setup binpatching
        {
            final Object patchedJar = chooseDeobfOutput(global, local, "", "binpatched");

            TaskApplyBinPatches task = makeTask(TASK_BINPATCH, TaskApplyBinPatches.class);
            task.setInJar(delayedFile(JAR_MERGED));
            task.setOutJar(patchedJar);
            task.setPatches(delayedFile(BINPATCH_USERDEV));
            task.setClassJar(delayedFile(ZIP_UD_CLASSES));
            task.setResourceJar(delayedFile(ZIP_UD_RES));
            task.dependsOn(TASK_MERGE_JARS, TASK_EXTRACT_USERDEV);

            project.getTasks().getByName(TASK_DEOBF_BIN).dependsOn(task);

            DeobfuscateJar deobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN).dependsOn(task);

            deobf.setInJar(patchedJar);
            deobf.dependsOn(task);
        }

        // setup source patching
        {
            final Object postDecompJar = chooseDeobfOutput(global, local, "", "decompFixed");
            final Object patchedJar = chooseDeobfOutput(global, local, "", "patched");

            PatchSourcesTask patch = makeTask(TASK_PATCH, PatchSourcesTask.class);
            patch.setPatches(delayedFile(ZIP_UD_PATCHES));
            patch.addInject(delayedFile(ZIP_UD_SRC));
            patch.addInject(delayedFile(ZIP_UD_RES)); // injecting teh resources too... the src jar needs them afterall.
            patch.setFailOnError(true);
            patch.setMakeRejects(false);
            patch.setPatchStrip(1);
            patch.setInJar(postDecompJar);
            patch.setOutJar(patchedJar);
            patch.dependsOn(TASK_POST_DECOMP);

            RemapSources remap = (RemapSources) project.getTasks().getByName(TASK_REMAP);
            remap.setInJar(patchedJar);
            remap.dependsOn(patch);
        }

        // setup reobf
        {
            TaskSingleReobf reobf = (TaskSingleReobf) project.getTasks().getByName(TASK_REOBF);
            reobf.addSecondarySrgFile(delayedFile(SRG_USERDEV));

            // still need to set the primary SRG
        }

    }

    @Override
    protected void afterEvaluate()
    {
        // add replacements
        T ext = getExtension();
        replacer.putReplacement(REPLACE_API_GROUP, getApiGroup(ext));
        replacer.putReplacement(REPLACE_API_GROUP_DIR, getApiGroup(ext).replace('.', '/'));
        replacer.putReplacement(REPLACE_API_NAME, getApiName(ext));
        replacer.putReplacement(REPLACE_API_VERSION, getApiVersion(ext));

        // read version file if exists
        {
            File jsonFile = delayedFile(JSON_USERDEV).call();
            if (jsonFile.exists())
            {
                parseAndStoreVersion(jsonFile, delayedFile(DIR_JSONS).call());
            }
        }

        super.afterEvaluate();

        // add userdev dep
        project.getDependencies().add(CONFIG_USERDEV, ImmutableMap.of(
                "group", getApiGroup(ext),
                "name", getApiName(ext),
                "version", getApiVersion(ext),
                "classifier", getUserdevClassifier(ext),
                "ext", getUserdevExtension(ext)
                ));
    }

    @Override
    protected void afterDecomp(final boolean isDecomp, final boolean useLocalCache, final String mcConfig)
    {
        // add MC repo to all projects
        project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                addFlatRepo(proj, "TweakerMcRepo", delayedFile(useLocalCache ? DIR_LOCAL_CACHE : DIR_API_JAR_BASE).call());
            }
        });

        // add the Mc dep
        T exten = getExtension();
        String group = getApiGroup(exten);
        String artifact = getApiName(exten) + (isDecomp ? "Src" : "Bin");
        String version = getApiVersion(exten) + (useLocalCache ? "-PROJECT(" + project.getName() + ")" : "");

        project.getDependencies().add(CONFIG_MC, ImmutableMap.of("group", group, "name", artifact, "version", version));
    }

    @Override
    protected Object getStartDir()
    {
        return delayedFile(DIR_API_BASE + "/start");
    }

    public abstract String getApiGroup(T ext);

    public abstract String getApiName(T ext);

    public abstract String getApiVersion(T ext);

    public abstract String getUserdevClassifier(T ext);

    public abstract String getUserdevExtension(T ext);

    //@formatter:off
    @Override protected boolean hasServerRun() { return true; }
    @Override protected boolean hasClientRun() { return true; }
    //@formatter:on
}

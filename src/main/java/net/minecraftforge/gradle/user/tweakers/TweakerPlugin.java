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
package net.minecraftforge.gradle.user.tweakers;

import static net.minecraftforge.gradle.common.Constants.DIR_LOCAL_CACHE;
import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.TASK_MERGE_JARS;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_MC;
import static net.minecraftforge.gradle.user.UserConstants.TASK_SETUP_CI;
import static net.minecraftforge.gradle.user.UserConstants.TASK_SETUP_DEV;

import java.io.File;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;

public abstract class TweakerPlugin extends UserBasePlugin<TweakerExtension>
{
    private static final String CLEAN_ROOT = REPLACE_CACHE_DIR + "/net/minecraft/";
    private static final String MCP_INSERT = Constants.REPLACE_MCP_CHANNEL + "/" + Constants.REPLACE_MCP_VERSION;

    @Override
    protected void applyUserPlugin()
    {
        // patterns
        String cleanSuffix = "%s-" + REPLACE_MC_VERSION;
        String dirtySuffix = "%s-" + REPLACE_MC_VERSION + "-PROJECT(" + project.getName() + ")";
        String jarName = getJarName();

        createDecompTasks(CLEAN_ROOT + jarName + "/" + REPLACE_MC_VERSION + "/" + MCP_INSERT + "/" + jarName + cleanSuffix, DIR_LOCAL_CACHE + "/" + jarName + dirtySuffix);

        // remove the unused merge jars task
        project.getTasks().remove(project.getTasks().getByName(TASK_MERGE_JARS));

        // add version json task to CI and dev workspace tasks
        project.getTasks().getByName(TASK_SETUP_CI).dependsOn(Constants.TASK_DL_VERSION_JSON);
        project.getTasks().getByName(TASK_SETUP_DEV).dependsOn(Constants.TASK_DL_VERSION_JSON);

        // add launchwrapper dep.. cuz everyone uses it apperantly..
        project.getDependencies().add(Constants.CONFIG_MC_DEPS, "net.minecraft:launchwrapper:1.11");
    }

    @Override
    protected void afterDecomp(final boolean isDecomp, final boolean useLocalCache, final String mcConfig)
    {
        // add MC repo to all projects
        project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                String cleanRoot = CLEAN_ROOT + getJarName() + "/" + REPLACE_MC_VERSION + "/" + MCP_INSERT;
                addFlatRepo(proj, "TweakerMcRepo", delayedFile(useLocalCache ? DIR_LOCAL_CACHE : cleanRoot).call());
            }
        });

        // add the Mc dep
        String group = "net.minecraft";
        String artifact = getJarName() + (isDecomp ? "Src" : "Bin");
        String version = delayedString(REPLACE_MC_VERSION).call() + (useLocalCache ? "-PROJECT(" + project.getName() + ")" : "");

        project.getDependencies().add(CONFIG_MC, ImmutableMap.of("group", group, "name", artifact, "version", version));
    }

    @Override
    protected void afterEvaluate()
    {
        // read version file if exists
        {
            File jsonFile = delayedFile(Constants.JSON_VERSION).call();
            if (jsonFile.exists())
            {
                parseAndStoreVersion(jsonFile, jsonFile.getParentFile());
            }
        }

        super.afterEvaluate();

        TweakerExtension ext = getExtension();

        if (Strings.isNullOrEmpty(ext.getTweakClass()))
        {
            throw new GradleConfigurationException("You must set the tweak class of your tweaker!");
        }

        // add fml tweaker to manifest
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        jarTask.getManifest().getAttributes().put("TweakClass", ext.getTweakClass());

    }

    /**
     * Correctly invoke the makeDecomptasks() method from the UserBasePlugin
     * @param globalPattern pattern for convenience
     * @param localPattern pattern for convenience
     */
    protected abstract void createDecompTasks(String globalPattern, String localPattern);

    /**
     * The name of the cached artifacts. The name o fthe API.. primary identifier.. thing.
     * @return "Minecraft" or "Minecraft_server" or something.
     */
    protected abstract String getJarName();

    @Override
    protected Object getStartDir()
    {
        return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/" + getJarName() + "/" + REPLACE_MC_VERSION + "/start");
    }

    @Override
    protected String getClientTweaker(TweakerExtension ext)
    {
        return "";// nothing, put it in as an argument
    }

    @Override
    protected String getServerTweaker(TweakerExtension ext)
    {
        return "";// nothing, put it in as an argument
    }

    @Override
    protected String getClientRunClass(TweakerExtension ext)
    {
        return "net.minecraft.launchwrapper.Launch"; // the launch wrapper
    }

    @Override
    protected List<String> getClientRunArgs(TweakerExtension ext)
    {
        List<String> out = ext.getResolvedClientRunArgs();
        out.add("--tweakClass");
        out.add(ext.getTweakClass());
        out.add("--noCoreSearch");// disabel FML-specific coremod loading
        return out;
    }

    @Override
    protected String getServerRunClass(TweakerExtension ext)
    {
        return "net.minecraft.launchwrapper.Launch"; // the launch wrapper
    }

    @Override
    protected List<String> getServerRunArgs(TweakerExtension ext)
    {
        List<String> out = ext.getResolvedServerRunArgs();
        out.add("--tweakClass");
        out.add(ext.getTweakClass());
        out.add("--noCoreSearch");// disabel FML-specific coremod loading
        return out;
    }

    @Override
    protected List<String> getClientJvmArgs(TweakerExtension ext)
    {
        return ext.getResolvedClientJvmArgs();
    }

    @Override
    protected List<String> getServerJvmArgs(TweakerExtension ext)
    {
        return ext.getResolvedServerJvmArgs();
    }
}

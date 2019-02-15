/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.user;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.gradle.common.Constants;
import org.gradle.api.Action;
import org.gradle.api.Project;

import java.io.File;
import java.util.List;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_MC;
import static net.minecraftforge.gradle.user.UserConstants.TASK_SETUP_CI;
import static net.minecraftforge.gradle.user.UserConstants.TASK_SETUP_DEV;

public abstract class UserVanillaBasePlugin<T extends UserBaseExtension> extends UserBasePlugin<T>
{
    private static final String CLEAN_ROOT = REPLACE_CACHE_DIR + "/net/minecraft/";
    private static final String MCP_INSERT = Constants.REPLACE_MCP_CHANNEL + "/" + Constants.REPLACE_MCP_VERSION;

    @Override
    protected final void applyUserPlugin()
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

        applyVanillaUserPlugin();
    }

    protected abstract void applyVanillaUserPlugin();

    @Override
    protected void afterDecomp(final boolean isDecomp, final boolean useLocalCache, final String mcConfig)
    {
        // add MC repo to all projects
        project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                String cleanRoot = CLEAN_ROOT + getJarName() + "/" + REPLACE_MC_VERSION + "/" + MCP_INSERT;
                addFlatRepo(proj, "VanillaMcRepo", delayedFile(useLocalCache ? DIR_LOCAL_CACHE : cleanRoot).call());
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
    }

    /**
     * Correctly invoke the makeDecomptasks() method from the UserBasePlugin
     * @param globalPattern pattern for convenience
     * @param localPattern pattern for convenience
     */
    protected abstract void createDecompTasks(String globalPattern, String localPattern);

    /**
     * The name of the cached artifacts. The name of the API.. primary identifier.. thing.
     * @return "Minecraft" or "Minecraft_server" or something.
     */
    protected abstract String getJarName();

    @Override
    protected Object getStartDir()
    {
        return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/" + getJarName() + "/" + REPLACE_MC_VERSION + "/start");
    }

    @Override
    protected List<String> getClientRunArgs(T ext)
    {
        List<String> out = ext.getResolvedClientRunArgs();
        return out;
    }

    @Override
    protected List<String> getServerRunArgs(T ext)
    {
        List<String> out = ext.getResolvedServerRunArgs();
        return out;
    }
}

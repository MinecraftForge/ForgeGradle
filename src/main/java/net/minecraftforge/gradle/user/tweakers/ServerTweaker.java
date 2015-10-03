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

import static net.minecraftforge.gradle.common.Constants.CONFIG_MC_DEPS;
import static net.minecraftforge.gradle.common.Constants.CONFIG_NATIVES;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_PURE;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_SERVER;
import static net.minecraftforge.gradle.common.Constants.REPLACE_ASSET_INDEX;
import static net.minecraftforge.gradle.common.Constants.TASK_SPLIT_SERVER;

import java.io.File;

import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import com.google.common.base.Throwables;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.json.JsonFactory;
import net.minecraftforge.gradle.util.json.version.Version;

public class ServerTweaker extends TweakerPlugin
{
    @Override
    protected String getJarName()
    {
        return "minecraft_server";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_SERVER_PURE), TASK_SPLIT_SERVER, delayedFile(MCP_PATCHES_SERVER));
    }

    /**
     * Mostly copy-pasted from the base plugin, except the mc-deps part
     */
    @Override
    protected Version parseAndStoreVersion(File file, File... inheritanceDirs)
    {
        if (!file.exists())
            return null;

        Version version = null;

        try
        {
            version = JsonFactory.loadVersion(file, delayedFile(Constants.DIR_JSONS).call());
        }
        catch (Exception e)
        {
            project.getLogger().error("" + file + " could not be parsed");
            Throwables.propagate(e);
        }

        if (version == null)
        {
            try
            {
                version = JsonFactory.loadVersion(file, delayedFile(Constants.DIR_JSONS).call());
            }
            catch (Exception e)
            {
                project.getLogger().error("" + file + " could not be parsed");
                Throwables.propagate(e);
            }
        }

        // apply the dep info.
        DependencyHandler handler = project.getDependencies();

        // actual dependencies
        if (project.getConfigurations().getByName(CONFIG_MC_DEPS).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.util.json.version.Library lib : version.getLibraries())
            {
                // CHANGE HERE *************
                if (lib.natives == null
                        // list of client-only things that shouldd be skipped
                        && !lib.name.contains("java3d")
                        && !lib.name.contains("paulscode")
                        && !lib.name.contains("lwjgl")
                        && !lib.name.contains("twitch")
                        && !lib.name.contains("jinput"))
                {

                    handler.add(CONFIG_MC_DEPS, lib.getArtifactName());
                }
                // END CHANGE *************
            }
        }
        else
            project.getLogger().debug("RESOLVED: " + CONFIG_MC_DEPS);

        // the natives
        if (project.getConfigurations().getByName(CONFIG_NATIVES).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.util.json.version.Library lib : version.getLibraries())
            {
                if (lib.natives != null)
                    handler.add(CONFIG_NATIVES, lib.getArtifactName());
            }
        }
        else
            project.getLogger().debug("RESOLVED: " + CONFIG_NATIVES);

        // set asset index
        replacer.putReplacement(REPLACE_ASSET_INDEX, version.getAssets());

        return version;
    }

    @Override
    protected boolean hasServerRun()
    {
        return true;
    }

    @Override
    protected boolean hasClientRun()
    {
        return false;
    }
}

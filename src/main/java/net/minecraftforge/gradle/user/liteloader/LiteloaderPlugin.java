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
package net.minecraftforge.gradle.user.liteloader;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserVanillaBasePlugin;

import java.util.List;

import static net.minecraftforge.gradle.common.Constants.*;

public class LiteloaderPlugin extends UserVanillaBasePlugin<LiteloaderExtension>
{
    @Override
    protected void applyVanillaUserPlugin()
    {
        // liteloader requires this...
        project.getDependencies().add(Constants.CONFIG_MC_DEPS, "net.minecraft:launchwrapper:1.11");
    }

    @Override
    protected String getJarName()
    {
        return "minecraft";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_CLIENT_FRESH), TASK_DL_CLIENT, delayedFile(MCP_PATCHES_CLIENT));
    }

    @Override
    protected boolean hasServerRun()
    {
        return false;
    }

    @Override
    protected boolean hasClientRun()
    {
        return true;
    }

    @Override
    protected Object getStartDir()
    {
        return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/" + getJarName() + "/" + REPLACE_MC_VERSION + "/start");
    }

    @Override
    protected String getClientTweaker(LiteloaderExtension ext)
    {
        return "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
    }

    @Override
    protected String getClientRunClass(LiteloaderExtension ext)
    {
        return "com.mumfrey.liteloader.debug.Start";
    }

    @Override
    protected String getServerTweaker(LiteloaderExtension ext)
    {
        return "";// never run on server.. so...
    }

    @Override
    protected String getServerRunClass(LiteloaderExtension ext)
    {
        // irrelevant..
        return "";
    }

    @Override
    protected List<String> getClientJvmArgs(LiteloaderExtension ext)
    {
        return ext.getResolvedClientJvmArgs();
    }

    @Override
    protected List<String> getServerJvmArgs(LiteloaderExtension ext)
    {
        return ext.getResolvedServerJvmArgs();
    }
}

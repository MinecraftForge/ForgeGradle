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
package net.minecraftforge.gradle.user.tweakers;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.Configuration;

import java.util.List;

import static net.minecraftforge.gradle.common.Constants.*;

public class ServerTweaker extends TweakerPlugin
{
    @Override
    protected void applyVanillaUserPlugin()
    {
        super.applyVanillaUserPlugin();

        // remove client deps
        Configuration config = project.getConfigurations().getByName(CONFIG_MC_DEPS);
        List<Configuration> configs = Lists.newArrayList(config.getExtendsFrom());
        configs.remove(project.getConfigurations().getByName(CONFIG_MC_DEPS_CLIENT));
        config.setExtendsFrom(configs);
    }

    @Override
    protected String getJarName()
    {
        return "minecraft_server";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_SERVER_PURE), TASK_SPLIT_SERVER, delayedFile(MCP_PATCHES_SERVER), delayedFile(MCP_INJECT));
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

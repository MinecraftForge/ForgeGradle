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

import java.util.List;

import net.minecraftforge.gradle.user.UserVanillaBasePlugin;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.GradleConfigurationException;

public abstract class TweakerPlugin extends UserVanillaBasePlugin<TweakerExtension>
{
    @Override
    protected void applyVanillaUserPlugin()
    {
        // add launchwrapper dep.. cuz everyone uses it apperantly..
        project.getDependencies().add(Constants.CONFIG_MC_DEPS, "net.minecraft:launchwrapper:1.11");
    }

    @Override
    protected void afterEvaluate()
    {
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
        return ext.getMainClass();
    }

    @Override
    protected List<String> getClientRunArgs(TweakerExtension ext)
    {
        List<String> out = super.getClientRunArgs(ext);
        out.add("--tweakClass");
        out.add(ext.getTweakClass());
        return out;
    }

    @Override
    protected String getServerRunClass(TweakerExtension ext)
    {
        return ext.getMainClass();
    }

    @Override
    protected List<String> getServerRunArgs(TweakerExtension ext)
    {
        List<String> out = super.getServerRunArgs(ext);
        out.add("--tweakClass");
        out.add(ext.getTweakClass());
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

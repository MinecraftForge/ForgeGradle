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
package net.minecraftforge.gradle.user.patcherUser.generic;

import java.util.List;

import net.minecraftforge.gradle.user.patcherUser.PatcherUserBasePlugin;

public class GenericPatcherUserPlugin extends PatcherUserBasePlugin<GenericPatcherUserExtension>
{
    @Override
    public String getApiGroup(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherGroup();
    }

    @Override
    public String getApiName(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherName();
    }

    @Override
    public String getApiVersion(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherVersion();
    }

    @Override
    public String getUserdevClassifier(GenericPatcherUserExtension ext)
    {
        return ext.getUserdevClassifier();
    }

    @Override
    public String getUserdevExtension(GenericPatcherUserExtension ext)
    {
        return ext.getUserdevExtension();
    }

    @Override
    protected String getClientTweaker(GenericPatcherUserExtension ext)
    {
        return ext.getClientTweaker();
    }

    @Override
    protected String getServerTweaker(GenericPatcherUserExtension ext)
    {
        return ext.getServerTweaker();
    }

    @Override
    protected String getClientRunClass(GenericPatcherUserExtension ext)
    {
        return ext.getClientRunClass();
    }

    @Override
    protected List<String> getClientRunArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedClientRunArgs();
    }

    @Override
    protected String getServerRunClass(GenericPatcherUserExtension ext)
    {
        return ext.getServerRunClass();
    }

    @Override
    protected List<String> getServerRunArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedServerRunArgs();
    }

    @Override
    protected List<String> getClientJvmArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedClientJvmArgs();
    }

    @Override
    protected List<String> getServerJvmArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedServerJvmArgs();
    }
}

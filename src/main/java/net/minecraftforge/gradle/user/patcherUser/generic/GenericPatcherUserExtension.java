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

import net.minecraftforge.gradle.user.UserBaseExtension;

public class GenericPatcherUserExtension extends UserBaseExtension
{
    private String patcherGroup, patcherName, patcherVersion;
    private String userdevClassifier = "userdev";
    private String userdevExtension  = "jar";
    private String clientTweaker, serverTweaker;
    private String clientRunClass    = "net.minecraft.launchwrapper.Launch";
    private String serverRunClass    = "net.minecraft.launchwrapper.Launch";

    public GenericPatcherUserExtension(GenericPatcherUserPlugin plugin)
    {
        super(plugin);
    }

    public String getPatcherGroup()
    {
        return patcherGroup;
    }

    public void setPatcherGroup(String patcherGroup)
    {
        this.patcherGroup = patcherGroup;
    }

    public String getPatcherName()
    {
        return patcherName;
    }

    public void setPatcherName(String patcherName)
    {
        this.patcherName = patcherName;
    }

    public String getPatcherVersion()
    {
        return patcherVersion;
    }

    public void setPatcherVersion(String patcherVersion)
    {
        this.patcherVersion = patcherVersion;
    }

    public String getUserdevClassifier()
    {
        return userdevClassifier;
    }

    public void setUserdevClassifier(String userdevClassifier)
    {
        this.userdevClassifier = userdevClassifier;
    }

    public String getUserdevExtension()
    {
        return userdevExtension;
    }

    public void setUserdevExtension(String userdevExtension)
    {
        this.userdevExtension = userdevExtension;
    }

    public String getClientTweaker()
    {
        return clientTweaker;
    }

    public void setClientTweaker(String clientTweaker)
    {
        this.clientTweaker = clientTweaker;
    }

    public String getServerTweaker()
    {
        return serverTweaker;
    }

    public void setServerTweaker(String serverTweaker)
    {
        this.serverTweaker = serverTweaker;
    }

    public String getClientRunClass()
    {
        return clientRunClass;
    }

    public void setClientRunClass(String clientRunClass)
    {
        this.clientRunClass = clientRunClass;
    }

    public String getServerRunClass()
    {
        return serverRunClass;
    }

    public void setServerRunClass(String serverRunClass)
    {
        this.serverRunClass = serverRunClass;
    }
}

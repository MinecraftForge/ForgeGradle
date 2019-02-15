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
package net.minecraftforge.gradle.util.caching;

import org.gradle.api.DefaultTask;

/**
 * This class offers some extra helper methods for caching files outside the project dir.
 * This is a convenience class that can be used instead of using the CacheContainer directly.
 */
public abstract class CachedTask extends DefaultTask implements ICachableTask
{
    private boolean doesCache = true;
    private boolean cacheSet  = false;

    public CachedTask()
    {
        super();
        CacheContainer.getCache(this);
    }

    protected boolean defaultCache()
    {
        return true;
    }

    @Override
    public boolean doesCache()
    {
        if (cacheSet)
            return doesCache;
        else
            return defaultCache();
    }

    public void setDoesCache(boolean cacheStuff)
    {
        this.cacheSet = true;
        this.doesCache = cacheStuff;
    }

    @Override
    public boolean cacheClassHash()
    {
        return false;
    }
}

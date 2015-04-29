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

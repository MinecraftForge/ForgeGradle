package net.minecraftforge.gradle.util.caching;

import org.gradle.api.Task;

public interface ICachableTask extends Task
{
    /**
     * Whether or not this task should actually be cached.
     * @return
     */
    public boolean doesCache();
    
    /**
     * Whether or not the hash of this task should be stored as an input.
     * Current unused.
     */
    public boolean cacheClassHash();
}

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;

public class CacheContainer
{
    public static CacheContainer getCache(ICachableTask task)
    {
        return pool.getUnchecked(task.getClass()).applyTo(task);
    }
    
    //@formatter:off
    private static final LoadingCache<Class<?>, CacheContainer> pool = CacheBuilder.newBuilder()
            .build(
                    new CacheLoader<Class<?>, CacheContainer>() {
                        @Override
                        public CacheContainer load(Class<?> key) throws Exception
                        {
                            return new CacheContainer(key);
                        }
                    });
    
    protected final List<Annotated> cachedList = Lists.newArrayList();
    protected final List<Annotated> inputList  = Lists.newArrayList();
    protected final List<WriteCacheAction> lastActions  = Lists.newArrayList();
    //@formatter:on

    private CacheContainer(Class<?> cacheable)
    {
        Class<?> task = cacheable;
        while (task != null && !task.getName().startsWith("org.gradle."))
        {
            for (Field f : task.getDeclaredFields())
            {
                if (f.isAnnotationPresent(Cached.class))
                {
                    addCachedOutput(new Annotated(task, f.getName()));
                }

                if (f.isAnnotationPresent(InputFile.class) ||
                        f.isAnnotationPresent(InputFiles.class) ||
                        f.isAnnotationPresent(InputDirectory.class) ||
                        f.isAnnotationPresent(Input.class))
                {
                    inputList.add(new Annotated(task, f.getName()));
                }
            }

            for (Method m : task.getDeclaredMethods())
            {
                if (m.isAnnotationPresent(Cached.class))
                {
                    addCachedOutput(new Annotated(task, m.getName(), true));
                }

                if (m.isAnnotationPresent(InputFile.class) ||
                        m.isAnnotationPresent(InputFiles.class) ||
                        m.isAnnotationPresent(InputDirectory.class) ||
                        m.isAnnotationPresent(Input.class))
                {
                    inputList.add(new Annotated(task, m.getName(), true));
                }
            }

            task = task.getSuperclass();
        }
    }

    private void addCachedOutput(final Annotated annot)
    {
        cachedList.add(annot);
        lastActions.add(new WriteCacheAction(annot, inputList));
    }

    public CacheContainer applyTo(ICachableTask task)
    {
        task.onlyIf(new CacheCheckSpec(this));
        for (WriteCacheAction a : lastActions)
        {
            task.doLast(a);
        }
        
        return this;
    }
}

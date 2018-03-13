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
package net.minecraftforge.gradle.util.delayed;

import groovy.lang.Closure;

@SuppressWarnings("serial")
public abstract class DelayedBase<V> extends Closure<V>
{
    protected TokenReplacer replacer;

    public DelayedBase(Class<?> owner, ReplacementProvider provider, String pattern)
    {
        super(owner);
        replacer = new TokenReplacer(provider, pattern);
    }
    
    public DelayedBase(Class<?> owner, TokenReplacer replacer)
    {
        super(owner);
        this.replacer = replacer;
    }

    /**
     * Does something with the replaced token and returns the proper type.
     * @param replaced NULL if never resolved before, else the previous resolved value
     * @return The resolved Object V
     */
    protected abstract V resolveDelayed(String replaced);
    
    @Override
    public final V call()
    {
        String replaced = null;
        if (replacer != null)
            replaced = replacer.replace();
        
        return resolveDelayed(replaced);
    }
    
    @Override
    public final V call(Object obj)
    {
        return call();
    }
    
    @Override
    public final V call(Object... objects)
    {
        return call();
    }

    @Override
    public String toString()
    {
        return call().toString();
    }
}

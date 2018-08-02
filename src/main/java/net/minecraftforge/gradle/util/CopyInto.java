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
package net.minecraftforge.gradle.util;

import java.util.HashMap;

import groovy.lang.Closure;
import net.minecraftforge.gradle.util.delayed.DelayedString;

import org.gradle.api.file.CopySpec;

import com.google.common.base.Strings;

@SuppressWarnings("serial")
public class CopyInto extends Closure<Object>
{
    private String dir;
    private String[] filters;
    private HashMap<String, Object> expands = new HashMap<String, Object>();
    
    public CopyInto(Class<?> owner, String dir)
    {
        super(owner);
        this.dir = dir;
        this.filters = new String[] {};
    }

    public CopyInto(Class<?> owner, String dir, String... filters)
    {
        super(owner);
        this.dir = dir;
        this.filters = filters;
    }
    
    public CopyInto addExpand(String key, String value)
    {
        expands.put(key, value);
        return this;
    }
    
    public CopyInto addExpand(String key, DelayedString value)
    {
        expands.put(key, value);
        return this;
    }

    @Override
    public Object call(Object... args)
    {
        CopySpec spec = (CopySpec)getDelegate();
        
        // do filters
        for (String s : filters)
        {
            if (s.startsWith("!")) spec.exclude(s.substring(1));
            else                   spec.include(s);
        }
        
        // expands
        
        if (!expands.isEmpty())
            spec.expand(expands);
        
        if (!Strings.isNullOrEmpty(dir))
            spec.into(dir);
        
        return null;
    }
};
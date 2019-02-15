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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class Annotated
{
    private final Class<?> clazz;
    private final String   symbolName;
    private final boolean  isMethod;

    public Annotated(Class<?> clazz, String symbolName, boolean isMethod)
    {
        this.clazz = clazz;
        this.symbolName = symbolName;
        this.isMethod = isMethod;
    }

    public Annotated(Class<?> clazz, String fieldName)
    {
        this.clazz = clazz;
        this.symbolName = fieldName;
        isMethod = false;
    }

    public AnnotatedElement getElement() throws NoSuchMethodException, NoSuchFieldException
    {
        if (isMethod)
            return clazz.getDeclaredMethod(symbolName);
        else
            return clazz.getDeclaredField(symbolName);
    }

    public Object getValue(Object instance) throws NoSuchMethodException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        Method method;

        if (isMethod)
            method = clazz.getDeclaredMethod(symbolName);
        else
        {
            // finds the getter, and uses that if possible.
            Field f = clazz.getDeclaredField(symbolName);
            String methodName = f.getType().equals(boolean.class) ? "is" : "get";

            char[] name = symbolName.toCharArray();
            name[0] = Character.toUpperCase(name[0]);
            methodName += new String(name);

            try
            {
                method = clazz.getMethod(methodName, new Class[0]);
            }
            catch (NoSuchMethodException e)
            {
                // method not found. Grab the field via reflection
                f.setAccessible(true);
                return f.get(instance);
            }
        }
        
        method.setAccessible(true);

        return method.invoke(instance, new Object[0]);
    }
}

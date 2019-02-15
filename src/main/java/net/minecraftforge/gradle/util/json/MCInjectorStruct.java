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
package net.minecraftforge.gradle.util.json;

import java.util.ArrayList;

public class MCInjectorStruct
{
    public EnclosingMethod enclosingMethod = null;
    public ArrayList<InnerClass> innerClasses = null;

    public static class EnclosingMethod
    {
        public final String desc;
        public final String name;
        public final String owner;

        EnclosingMethod(String owner, String name, String desc)
        {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }

    public static class InnerClass
    {
        public String access;
        public final String inner_class;
        public final String inner_name;
        public final String outer_class;
        public final String start;

        InnerClass(String inner_class, String outer_class, String inner_name, String access, String start)
        {
            this.inner_class = inner_class;
            this.outer_class = outer_class;
            this.inner_name = inner_name;
            this.access = access;
            this.start = start;
        }

        public int getAccess() { return Integer.parseInt(access == null ? "0" : access, 16); }
        public int getStart()  { return Integer.parseInt(start  == null ? "0" : start,  10); }
    }
}

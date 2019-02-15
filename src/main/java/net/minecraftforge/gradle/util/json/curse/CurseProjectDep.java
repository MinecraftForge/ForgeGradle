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
package net.minecraftforge.gradle.util.json.curse;

public class CurseProjectDep
{

    /** The unique slug of the project */
    public String slug;

    /** The type of dependency. {@code embeddedLibrary, optionalLibrary, requiredLibrary, tool, or incompatible} */
    public String type;

    public CurseProjectDep(final String slug, final String type)
    {
        this.slug = slug;
        this.type = type;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        CurseProjectDep that = (CurseProjectDep) o;
        return !(slug != null ? !slug.equals(that.slug) : that.slug != null);
    }

    @Override
    public int hashCode()
    {
        return slug != null ? slug.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return "CurseProjectDep{" +
                "slug='" + slug + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}

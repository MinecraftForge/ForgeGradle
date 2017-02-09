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
package net.minecraftforge.gradle.user.liteloader;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HashMap;

public class LiteModDescription extends HashMap<String, Object>
{
    public static final String BASE = "";

    private static final long serialVersionUID = 1L;

    @Override
    public String toString()
    {
        Object value = this.get(LiteModDescription.BASE);
        return value == null ? LiteModDescription.BASE : value.toString();
    }

    public void propertyMissing(String name, Object value)
    {
        this.put(name, value);
    }

    public Object propertyMissing(String name)
    {
        return this.get(name);
    }

    static class JsonAdapter extends TypeAdapter<LiteModDescription>
    {
        @Override
        public void write(JsonWriter out, LiteModDescription value) throws IOException
        {
            if (value == null)
            {
                out.nullValue();
                return;
            }

            out.value(value.toString());
            for (Entry<String, Object> entry : value.entrySet())
            {
                if (!entry.getKey().equals(LiteModDescription.BASE) && entry.getValue() != null)
                {
                    out.name("description." + entry.getKey()).value(entry.getValue().toString());
                }
            }
        }

        @Override
        public LiteModDescription read(JsonReader in) throws IOException
        {
            return null;
        }
    }
}

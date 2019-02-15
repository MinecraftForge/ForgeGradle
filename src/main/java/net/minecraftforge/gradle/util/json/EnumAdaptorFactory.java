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

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class EnumAdaptorFactory implements TypeAdapterFactory
{

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
    {
        if (!type.getRawType().isEnum()) return null;
        final Map<String, T> map = new HashMap<String, T>();
        for (T c : (T[])type.getRawType().getEnumConstants())
        {
            map.put(c.toString().toLowerCase(Locale.US), c);
        }

        return new TypeAdapter<T>()
        {
            @Override
            public T read(JsonReader reader) throws IOException
            {
                if (reader.peek() == JsonToken.NULL)
                {
                    reader.nextNull();
                    return null;
                }
                String name = reader.nextString();
                if (name == null) return null;
                return map.get(name.toLowerCase(Locale.US));
            }

            @Override
            public void write(JsonWriter writer, T value) throws IOException
            {
                if (value == null)
                {
                    writer.nullValue();
                }
                else
                {
                    writer.value(value.toString().toLowerCase(Locale.US));
                }
            }
        };
    }
}

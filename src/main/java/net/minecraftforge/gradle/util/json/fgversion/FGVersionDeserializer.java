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
package net.minecraftforge.gradle.util.json.fgversion;

import java.lang.reflect.Type;
import java.util.List;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

public class FGVersionDeserializer implements JsonDeserializer<FGVersionWrapper>
{
    @Override
    public FGVersionWrapper deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException
    {
        FGVersionWrapper wrapper = new FGVersionWrapper();

        List<FGVersion> versions = context.deserialize(json, new TypeToken<List<FGVersion>>() {}.getType());

        for (int i = 0; i < versions.size(); i++)
        {
            FGVersion v = versions.get(i);
            v.index = i;
            wrapper.versions.add(v.version);
            wrapper.versionObjects.put(v.version, v);
        }

        return wrapper;
    }
}

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

import com.google.common.collect.Maps;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.minecraftforge.gradle.util.json.version.ManifestVersion;

import java.lang.reflect.Type;
import java.util.Map;

public class MojangManifestAdapter implements JsonDeserializer<Map<String, ManifestVersion>>
{
    @Override
    public Map<String, ManifestVersion> deserialize(JsonElement json, Type type, JsonDeserializationContext context) throws JsonParseException
    {
        Map<String, ManifestVersion> out = Maps.newHashMap();

        for (JsonElement element : json.getAsJsonObject().get("versions").getAsJsonArray())
        {
            ManifestVersion version = context.deserialize(element, ManifestVersion.class);
            out.put(version.id, version);
        }

        return out;
    }
}

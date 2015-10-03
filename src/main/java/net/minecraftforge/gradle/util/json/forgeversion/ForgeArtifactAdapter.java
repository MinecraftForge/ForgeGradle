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
package net.minecraftforge.gradle.util.json.forgeversion;

import java.io.IOException;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ForgeArtifactAdapter extends TypeAdapter<ForgeArtifact>
{

    @Override
    public void write(JsonWriter out, ForgeArtifact value) throws IOException
    {
        // dont really need to do this.. but wtvr...
        out.beginArray();
        out.value(value.ext);
        out.value(value.classifier);
        out.value(value.hash);
        out.endArray();
    }

    @Override
    public ForgeArtifact read(JsonReader in) throws IOException
    {
        ForgeArtifact out = new ForgeArtifact();
        
        in.beginArray();

        out.ext = in.nextString();
        out.classifier = in.nextString();
        out.hash = in.nextString();
        
        in.endArray();
        
        return out;
    }

}

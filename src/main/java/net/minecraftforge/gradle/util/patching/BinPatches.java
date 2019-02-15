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
package net.minecraftforge.gradle.util.patching;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.nothome.delta.Delta;

import java.io.*;
import java.util.zip.*;

public class BinPatches
{
    private BinPatches()
    {
        throw new RuntimeException("Utility class should not be instantiated");
    }

    public static byte[] getBinPatchBytesWithHeader(Delta delta, String cleanName, String srgName, byte[] clean, byte[] dirty) throws IOException
    {
        byte[] diff = delta.compute(clean == null ? new byte[0] : clean, dirty);

        ByteArrayDataOutput out = ByteStreams.newDataOutput(diff.length + 50);
        out.writeUTF(cleanName);                   // Clean name
        out.writeUTF(cleanName.replace('/', '.')); // Source Notch name
        out.writeUTF(srgName.replace('/', '.')); // Source SRG Name
        out.writeBoolean(clean != null);    // Exists in Clean
        if (clean != null)
        {
            out.writeInt(adlerHash(clean)); // Hash of Clean file
        }
        out.writeInt(diff.length); // Patch length
        out.write(diff);           // Patch
        return out.toByteArray();
    }

    private static int adlerHash(byte[] input)
    {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return (int) hasher.getValue();
    }
}

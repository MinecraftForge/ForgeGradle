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
package net.minecraftforge.gradle.user.patcherUser.forge;

import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.ReobfTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class McVersionTransformer implements ReobfTransformer
{
    private static final long serialVersionUID = 1L;

    private Object            mcVersion;

    protected McVersionTransformer(Object mcVersion)
    {
        this.mcVersion = mcVersion;
    }

    @Override
    public byte[] transform(byte[] data)
    {
        String mcVersion = Constants.resolveString(this.mcVersion);

        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();

        reader.accept(node, 0);
        List<AnnotationNode> annots = node.visibleAnnotations;

        if (annots == null || annots.isEmpty()) // annotations
            return data;

        for (AnnotationNode mod : annots)
        {
            if (mod.desc.endsWith("fml/common/Mod;"))
            {
                int index = mod.values.indexOf("acceptedMinecraftVersions");
                if (index == -1)
                {
                    mod.values.add("acceptedMinecraftVersions");
                    mod.values.add("[" + mcVersion + "]");
                }

                break; // break out, im done. There cant be 2 @Mods in a file... can there?
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}

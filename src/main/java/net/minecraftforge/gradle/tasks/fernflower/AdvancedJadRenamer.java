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
package net.minecraftforge.gradle.tasks.fernflower;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.JADNameProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AdvancedJadRenamer extends JADNameProvider {
    private StructMethod wrapper;
    private static final Pattern p = Pattern.compile("func_(\\d+)_.*");
    public AdvancedJadRenamer(StructMethod wrapper)
    {
        super(wrapper);
        this.wrapper = wrapper;
    }
    @Override
    public String renameAbstractParameter(String abstractParam, int index)
    {
        String result = abstractParam;
        if ((wrapper.getAccessFlags() & CodeConstants.ACC_ABSTRACT) != 0) {
            String methName = wrapper.getName();
            Matcher m = p.matcher(methName);
            if (m.matches()) {
                result = String.format("p_%s_%d_", m.group(1), index);
            }
        }
        return result;

    }
}

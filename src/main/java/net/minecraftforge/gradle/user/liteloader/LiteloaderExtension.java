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
package net.minecraftforge.gradle.user.liteloader;

import com.google.common.base.Strings;
import net.minecraftforge.gradle.user.UserBaseExtension;
import org.gradle.api.InvalidUserDataException;
import org.gradle.jvm.tasks.Jar;

public class LiteloaderExtension extends UserBaseExtension
{
    private final LiteloaderPlugin plugin;
    
    public LiteloaderExtension(LiteloaderPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
    }
    
    @Override
    public void setVersion(String version)
    {
        super.setVersion(version);
        this.checkVersion(version);
        
        Jar jar = (Jar)project.getTasks().getByName("jar");
        if (Strings.isNullOrEmpty(jar.getClassifier())) {
            jar.setClassifier("mc" + version);
        }
    }

    private void checkVersion(String version)
    {
        if (this.plugin.getVersion(version) == null)
        {
            throw new InvalidUserDataException("No ForgeGradle-compatible LiteLoader version found for Minecraft" + version);
        }
    }
}

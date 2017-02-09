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
package net.minecraftforge.gradle.user.patcherUser.forge;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.tasks.AbstractJsonTask;

public class ForgeInfoTask extends AbstractJsonTask<ForgeModInfo>
{

    public ForgeInfoTask()
    {
        this.setFileName("mcmod.info");
    }

    public ForgeModInfo getInfo()
    {
        // so it can be accessed via the filename. (mcmod.info.version = 3)
        return this.getJson();
    }

    public void info(Closure<?> cl)
    {
        super.json(cl);
    }

    @Override
    protected ForgeModInfo createJson()
    {
        BaseExtension ext = (BaseExtension) getProject().getExtensions().findByName("minecraft");
        return new ForgeModInfo(ext.getVersion());
    }
}

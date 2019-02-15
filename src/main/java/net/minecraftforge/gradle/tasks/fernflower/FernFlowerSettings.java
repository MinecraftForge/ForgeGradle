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

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class FernFlowerSettings implements Serializable {

    private static final long serialVersionUID = -7610967091668947017L;

    private final File cacheDirectory;
    private final File jarFrom;
    private final File jarTo;
    private final File taskLogFile;
    private final Set<File> classpath;
    // note: while this field is String->Object, realistically only Strings
    // should be entered.
    private final Map<String, Object> mapOptions;

    public FernFlowerSettings(File cacheDirectory, File jarFrom, File jarTo, File taskLogFile, Set<File> classpath, Map<String, Object> mapOptions)
    {
        this.cacheDirectory = cacheDirectory;
        this.jarFrom = jarFrom;
        this.jarTo = jarTo;
        this.taskLogFile = taskLogFile;
        this.classpath = classpath;
        this.mapOptions = mapOptions;
    }

    public File getCacheDirectory()
    {
        return cacheDirectory;
    }

    public File getJarFrom()
    {
        return jarFrom;
    }

    public File getJarTo()
    {
        return jarTo;
    }

    public File getTaskLogFile()
    {
        return taskLogFile;
    }

    public Set<File> getClasspath()
    {
        return classpath;
    }

    public Map<String, Object> getMapOptions()
    {
        return mapOptions;
    }

    @Override
    public String toString()
    {
        return "FernFlowerSettings[cacheDirectory=" + cacheDirectory + ",jarFrom=" + jarFrom + ",jarTo=" + jarTo + ",taskLogFile=" + taskLogFile + ",classpath=" + classpath + ",mapOptions=" + mapOptions + "]";
    }

}

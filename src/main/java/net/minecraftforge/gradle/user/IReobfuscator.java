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
package net.minecraftforge.gradle.user;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.gradle.api.file.FileCollection;

/**
 * Used to create a base interface for the reobf task and config classes so that
 * things can be copy-pasted between the two.
 */
public interface IReobfuscator
{

    /**
     * Gets the mappings file used to reobfuscate. It should be either a
     * {@link File} or a String path for a DelayedFile.
     * 
     * @return The srg file or path to it
     */
    Object getMappings();

    /**
     * Sets the mappings file used to reobfuscate. It should be either a String
     * or {@link File}.
     * 
     * @param srg The srg file or path to it
     */
    void setMappings(Object srg);

    /**
     * Sets the classpath used to reobfuscate. This is used by groovy for
     * simplicity. Use <code>classpath += otherClasspath</code> to add to it.
     * 
     * @param classpath The new classpath
     */
    void setClasspath(FileCollection classpath);

    /**
     * Gets the classpath used to reobfuscate. Use
     * <code>classpath += otherClasspath</code> to add to it.
     * 
     * @return The classpath
     */
    FileCollection getClasspath();

    /**
     * Gets the extra srg lines and files. Modders should prefer to use
     * {@link #extra(Object...)} or {@code extra += []} instead of setting the
     * list manually.
     * 
     * @return The extra srg files or lines
     */
    List<Object> getExtra();

    /**
     * Sets the extra lines and files. Modders should prefer to use
     * {@link #extra(Object...)} or {@code extra += []} instead of setting the
     * list manually.
     * 
     * @param extra The list of srgs
     */
    void setExtra(List<Object> extra);

    /**
     * Adds some additional srg files or lines for reobfuscating. Should be a
     * file or string path
     * 
     * @param o The array to add
     */
    void extra(Object... o);

    /**
     * Adds a collection of additional srg files or lines for reobfuscating.
     * 
     * @param o The collection to add
     */
    void extra(Collection<Object> o);

    /**
     * Sets the mappings to use Searge names. This is the default with the Forge
     * plugin.
     *
     * i.e. Minecraft.func_71410_x()
     */
    void useSrgSrg();

    /**
     * Sets the mappings to use Notch names. Useful for mods that want to be
     * able to run without Forge installed, such as libraries or hybrid mods.
     *
     * i.e. bsu.z()
     */
    void useNotchSrg();

}

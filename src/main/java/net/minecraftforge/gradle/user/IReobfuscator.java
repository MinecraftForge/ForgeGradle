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
package net.minecraftforge.gradle.user;

import java.io.File;
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
     * Gets the mappings type.
     *
     * @return The mapping type
     */
    ReobfMappingType getMappingType();

    /**
     * Sets the mapping type for easy access to searge or notch names. For
     * custom mappings, use {@link #setMappings(Object)}.
     *
     * <pre>
     * // for notch names (vanilla)
     * mappingType = "NOTCH"
     *
     * // or searge names (forge)
     * mappingType = "SEARGE"
     * </pre>
     *
     * @param type The mapping
     * @throws NullPointerException when type is null
     * @throws IllegalArgumentException when type is {@link ReobfMappingType#CUSTOM}
     */
    void setMappingType(ReobfMappingType type);

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
     * {@link #extraLines(Object...)} or {@code extra += []} instead of setting
     * the list manually.
     * 
     * @return The extra srg lines
     */
    List<Object> getExtraLines();

    /**
     * Sets the extra lines. Modders should prefer to use
     * {@link #extraLines(Object...)} instead of setting the list manually.
     * 
     * @param extra The list of srg lines
     */
    void setExtraLines(List<Object> extra);

    /**
     * Adds some additional srg lines for reobfuscating. These are resolved to
     * strings.
     * 
     * @param o The array to add
     */
    void extraLines(Object... o);

    /**
     * Adds a collection of additional srg lines for reobfuscating.
     * 
     * @param o The collection to add
     */
    void extraLines(Iterable<Object> o);

    /**
     * Gets the extra srg files. Modders should prefer to use
     * {@link #extraFiles(Object...)} instead of setting the list manually.
     * 
     * @return The extra srg files
     */
    List<Object> getExtraFiles();

    /**
     * Adds some additional srg files for reobfuscating. These are resolved to
     * files with {@link org.gradle.api.Project#file(Object)}
     * 
     * @param o The array to add
     */
    void extraFiles(Object... o);

    /**
     * Adds a collection of additional srg files for reobfuscating.
     * 
     * @param o The collection to add
     */
    void extraFiles(Iterable<Object> o);

    /**
     * Sets the mappings to use Searge names. This is the default with the Forge
     * plugin.
     *
     * i.e. Minecraft.func_71410_x()
     *
     * @deprecated Use {@link #setMappingType(ReobfMappingType)}
     */
    @Deprecated
    void useSrgSrg();

    /**
     * Sets the mappings to use Notch names. Useful for mods that want to be
     * able to run without Forge installed, such as libraries or hybrid mods.
     *
     * i.e. bsu.z()
     *
     * @deprecated Use {@link #setMappingType(ReobfMappingType)}
     */
    @Deprecated
    void useNotchSrg();

}

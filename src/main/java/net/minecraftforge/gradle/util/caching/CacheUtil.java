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
package net.minecraftforge.gradle.util.caching;

import groovy.lang.Closure;

import java.io.File;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.util.PatternSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

class CacheUtil
{
    public static final Logger LOGGER = LoggerFactory.getLogger(CacheUtil.class);

    protected static File getHashFile(File file)
    {
        if (file.isDirectory())
            return new File(file, ".cache");
        else
            return new File(file.getParentFile(), file.getName() + ".md5");
    }

    @SuppressWarnings("rawtypes")
    protected static String getHashes(Annotated output, List<Annotated> inputs, ICachableTask task) throws NoSuchMethodException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        // TODO: CONVERT TO CacheFile
        List<String> hashes = Lists.newArrayListWithCapacity(inputs.size() + 5);

        hashes.addAll(Constants.hashAll(task.getProject().file(output.getValue(task))));

        for (Annotated input : inputs)
        {
            AnnotatedElement m = input.getElement();
            Object val = input.getValue(task);

            if (val == null && m.isAnnotationPresent(Optional.class))
            {
                hashes.add("null");
            }
            else if (m.isAnnotationPresent(InputFile.class))
            {
                hashes.add(Constants.hash(task.getProject().file(input.getValue(task))));
                LOGGER.debug(Constants.hash(task.getProject().file(input.getValue(task))) + " " + input.getValue(task));
            }
            else if (m.isAnnotationPresent(InputDirectory.class))
            {
                File dir = (File) input.getValue(task);
                hashes.addAll(Constants.hashAll(dir));
            }
            else if (m.isAnnotationPresent(InputFiles.class))
            {
                FileCollection files = (FileCollection) input.getValue(task);
                for (File file : files.getFiles())
                {
                    String hash = Constants.hash(file);
                    hashes.add(hash);
                    LOGGER.debug(hash + " " + input.getValue(task));
                }
            }
            else
            // just @Input
            {
                Object obj = input.getValue(task);

                while (obj instanceof Closure)
                    obj = ((Closure) obj).call();

                if (obj instanceof String)
                {
                    hashes.add(Constants.hash((String) obj));
                    LOGGER.debug(Constants.hash((String) obj) + " " + (String) obj);
                }
                else if (obj instanceof File)
                {
                    File file = (File) obj;
                    if (file.isDirectory())
                    {
                        List<File> files = Arrays.asList(file.listFiles());
                        Collections.sort(files);
                        for (File i : files)
                        {
                            hashes.add(Constants.hash(i));
                            LOGGER.debug(Constants.hash(i) + " " + i);
                        }
                    }
                    else
                    {
                        hashes.add(Constants.hash(file));
                        LOGGER.debug(Constants.hash(file) + " " + file);
                    }
                }
                else if (obj instanceof PatternSet)
                {
                    PatternSet set = (PatternSet)obj;
                    hashes.add(Constants.hash(
                            "" +
                            set.isCaseSensitive() + " " +
                            set.getIncludes().toString() + " " +
                            set.getExcludes().toString() + " " +
                            set.getIncludeSpecs().size() + " " +
                            set.getExcludeSpecs().size()
                            ));
                }
                else
                {
                    hashes.add(Constants.hash(obj.toString()));
                }
            }
        }

        return Joiner.on(Constants.NEWLINE).join(hashes);
    }
}

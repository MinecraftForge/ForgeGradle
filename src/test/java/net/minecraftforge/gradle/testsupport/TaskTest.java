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
package net.minecraftforge.gradle.testsupport;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.*;

/**
 * Base class for task tests
 */
public abstract class TaskTest<T extends DefaultTask> implements UsesTemporaryFiles
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    protected T getTask(Class<? extends T> clazz) throws IOException
    {
        Project project = ProjectBuilder.builder()
                .withProjectDir(temporaryFolder.newFolder("projectDir"))
                .build();
        return project.getTasks().create(clazz.getSimpleName(), clazz);
    }
}

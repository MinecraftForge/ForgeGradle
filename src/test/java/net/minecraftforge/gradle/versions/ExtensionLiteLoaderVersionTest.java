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
package net.minecraftforge.gradle.versions;

import static org.junit.Assert.*;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.gradle.user.liteloader.LiteloaderExtension;
import net.minecraftforge.gradle.user.liteloader.LiteloaderPlugin;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

public class ExtensionLiteLoaderVersionTest
{
    private Project             testProject;
    private LiteloaderExtension ext;

    @Before
    public void setupProject()
    {
        this.testProject = ProjectBuilder.builder().build();
        assertNotNull(this.testProject);
        this.testProject.apply(ImmutableMap.of("plugin", LiteloaderPlugin.class));

        this.ext = this.testProject.getExtensions().findByType(LiteloaderExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
    }

    // Invalid version notation! The following are valid notations. BuildNumber, version, version-branch, mcversion-version-branch, and pomotion (sic)

    @Test
    public void testValidVersion()
    {
        // version
        this.ext.setVersion("1.8.9");
        assertEquals(this.ext.getVersion(), "1.8.9");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testInvalidMcVersion()
    {
        // invalid MC version
        this.ext.setVersion("1.2.3");
    }
}

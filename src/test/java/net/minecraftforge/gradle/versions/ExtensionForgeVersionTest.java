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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgeExtension;
import net.minecraftforge.gradle.user.patcherUser.forge.ForgePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ExtensionForgeVersionTest
{
    private Project        testProject;
    private ForgeExtension ext;

    @Before
    public void setupProject()
    {
        this.testProject = ProjectBuilder.builder().build();
        assertNotNull(this.testProject);
        this.testProject.apply(ImmutableMap.of("plugin", ForgePlugin.class));

        this.ext = this.testProject.getExtensions().findByType(ForgeExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
    }

    // Invalid version notation! The following are valid notations. BuildNumber, version, version-branch, mcversion-version-branch, and pomotion (sic)

    @Test
    public void testValidBuildNumber()
    {
        // buildnumber
        this.ext.setVersion("965");
        assertEquals(this.ext.getVersion(), "1.6.4");
        assertEquals(this.ext.getForgeVersion(), "9.11.1.965");
    }

    @Test
    public void testValidPromotion()
    {
        // promotion
        this.ext.setVersion("1.6.4-recommended");
        assertEquals(this.ext.getVersion(), "1.6.4");
        assertEquals(this.ext.getForgeVersion(), "9.11.1.1345");
    }

    @Test
    public void testValidBuildNumberNoBranch()
    {
        // buildnumber (no branch)
        this.ext.setVersion("1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getForgeVersion(), "10.13.2.1256");
    }

    @Test
    public void testValidBuildNumberWithBranch()
    {
        // buildnumber (with branch)
        this.ext.setVersion("1257");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getForgeVersion(), "11.14.0.1257-1.8");
    }

    @Test
    public void testValidVersion()
    {
        // version
        this.ext.setVersion("10.13.2.1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getForgeVersion(), "10.13.2.1256");
    }

    @Test
    public void testValidMcVersionWithVersion()
    {
        // mcversion-version
        this.ext.setVersion("1.7.10-10.13.2.1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getForgeVersion(), "10.13.2.1256");
    }

    @Test
    public void testValidVersionWithBranch()
    {
        // version-branch
        this.ext.setVersion("11.14.0.1257-1.8");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getForgeVersion(), "11.14.0.1257-1.8");
    }

    @Test
    public void testValidMcVersionWithVersionAndBranch()
    {
        // mcversion-version-branch
        this.ext.setVersion("1.8-11.14.0.1257-1.8");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getForgeVersion(), "11.14.0.1257-1.8");
    }

    // Invalid formats

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidBuild()
    {
        // 1.8 build skipped due to 1.7.10
        this.ext.setVersion("11.14.0.1256-1.8");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidBuildWithMcVersion()
    {
        // 1.8 build skipped due to 1.7.10 (with MC version)
        this.ext.setVersion("1.8-11.14.0.1256-1.8");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidMcVersion()
    {
        // invalid MC version
        this.ext.setVersion("1.7.10-9.11.1.965");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidMcVersionWithBranch()
    {
        // invalid MC version (with branch)
        this.ext.setVersion("1.7.10-11.14.0.1257-1.8");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidBranch()
    {
        // invalid branch
        this.ext.setVersion("1.7.10-11.14.0.1256-1.8");
    }
}

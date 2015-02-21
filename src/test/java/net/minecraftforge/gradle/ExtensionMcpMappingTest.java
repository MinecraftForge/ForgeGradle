package net.minecraftforge.gradle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import net.minecraftforge.gradle.user.patch.ForgeUserPlugin;
import net.minecraftforge.gradle.user.patch.UserPatchExtension;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class ExtensionMcpMappingTest
{
    private Project            testProject;
    private UserPatchExtension ext;

    @Before
    public void setupProject()
    {
        this.testProject = ProjectBuilder.builder().build();
        assertNotNull(this.testProject);
        this.testProject.apply(ImmutableMap.of("plugin", ForgeUserPlugin.class));

        this.ext = this.testProject.getExtensions().findByType(UserPatchExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
    }

    private static final String VERSION_17 = "1.7.10-10.13.2.1291";
    private static final String VERSION_18 = "1.8-11.14.1.1320";

    @Test
    public void testValidSnapshot17()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_20140925");
        assertEquals(this.ext.getMappingsChannel(), "snapshot");
        assertEquals(this.ext.getMappingsVersion(), "20140925");
    }

    @Test
    public void testValidStable17()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_12");
        assertEquals(this.ext.getMappingsChannel(), "stable");
        assertEquals(this.ext.getMappingsVersion(), "12");
    }

    @Test
    public void testValidSnapshot18()
    {
        this.ext.setVersion(VERSION_18);
        this.ext.setMappings("snapshot_20150218");
        assertEquals(this.ext.getMappingsChannel(), "snapshot");
        assertEquals(this.ext.getMappingsVersion(), "20150218");
    }

    @Test
    public void testValidStable18()
    {
        this.ext.setVersion(VERSION_18);
        this.ext.setMappings("stable_15");
        assertEquals(this.ext.getMappingsChannel(), "stable");
        assertEquals(this.ext.getMappingsVersion(), "15");
    }

    @Test
    public void testSnapshotNodoc()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_nodoc_20140925");
        assertEquals(this.ext.getMappingsChannelNoSubtype(), "snapshot");
        assertEquals(this.ext.getMappingsVersion(), "20140925");
    }

    @Test
    public void testStableNodoc()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_nodoc_12");
        assertEquals(this.ext.getMappingsChannelNoSubtype(), "stable");
        assertEquals(this.ext.getMappingsVersion(), "12");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidSnapshot17()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_20141205");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidSnapshot18()
    {
        this.ext.setVersion(VERSION_18);
        this.ext.setMappings("snapshot_20140909");
    }

    @Test
    public void testOrdering()
    {
        this.ext.setMappings("snapshot_20140925");
        this.ext.setVersion(VERSION_17);
        assertEquals(this.ext.getMappingsChannel(), "snapshot");
        assertEquals(this.ext.getMappingsVersion(), "20140925");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidSnapshot()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("snapshot_15");
    }

    @Test(expected = GradleConfigurationException.class)
    public void testInvalidStable()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("stable_20140925");
    }
    
    @Test(expected = GradleConfigurationException.class)
    public void testInvalidCustom()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("abrar_blahblah");
    }
    
    public void testValidCustom()
    {
        this.ext.setVersion(VERSION_17);
        this.ext.setMappings("abrar_custom");
        assertEquals(this.ext.getMappingsChannel(), "abrar");
        assertEquals(this.ext.getMappingsVersion(), "custom");
    }
}

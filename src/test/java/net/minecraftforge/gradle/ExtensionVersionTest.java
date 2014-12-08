package net.minecraftforge.gradle;

import java.io.File;
import java.util.Map;
import java.util.HashMap;

import net.minecraftforge.gradle.user.patch.ForgeUserPlugin;
import net.minecraftforge.gradle.user.patch.UserPatchExtension;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

public class ExtensionVersionTest
{
    private Project testProject;
    private UserPatchExtension ext;

    @Before
    public void setupProject()
    {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("plugin", ForgeUserPlugin.class);

        this.testProject = ProjectBuilder.builder().build();
        assertNotNull(this.testProject);
        this.testProject.apply(options);

        this.ext = this.testProject.getExtensions().findByType(UserPatchExtension.class);   // unlike getByType(), does not throw exception
        assertNotNull(this.ext);
    }

    // Invalid version notation! The following are valid notations. Buildnumber, version, version-branch, mcversion-version-branch, and pomotion (sic)

    @Test
    public void testValidBuildNumber()
    {
        // buildnumber
        this.ext.setVersion("965");
        assertEquals(this.ext.getVersion(), "1.6.4");
        assertEquals(this.ext.getApiVersion(), "1.6.4-9.11.1.965");
    }

    @Test
    public void testValidPromotion()
    {
        // promotion
        this.ext.setVersion("1.6.4-recommended");
        assertEquals(this.ext.getVersion(), "1.6.4");
        assertEquals(this.ext.getApiVersion(), "1.6.4-9.11.1.965");
    }

    @Test
    public void testValidPromotionWithBranch()
    {
        // promotion (with branch)
        this.ext.setVersion("1.7.10-latest-new");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getApiVersion(), "1.7.10-10.13.1.1216-new");
    }

    @Test
    public void testValidBuildNumberNoBranch()
    {
        // buildnumber (no branch)
        this.ext.setVersion("1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getApiVersion(), "1.7.10-10.13.2.1256");
    }

    @Test
    public void testValidBuildNumberWithBranch()
    {
        // buildnumber (with branch)
        this.ext.setVersion("1257");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getApiVersion(), "1.8-11.14.0.1257-1.8");
    }

    @Test
    public void testValidVersion()
    {
        // version
        this.ext.setVersion("10.13.2.1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getApiVersion(), "1.7.10-10.13.2.1256");
    }

    @Test
    public void testValidMcVersionWithVersion()
    {
        // mcversion-version
        this.ext.setVersion("1.7.10-10.13.2.1256");
        assertEquals(this.ext.getVersion(), "1.7.10");
        assertEquals(this.ext.getApiVersion(), "1.7.10-10.13.2.1256");
    }

    @Test
    public void testValidVersionWithBranch()
    {
        // version-branch
        this.ext.setVersion("11.14.0.1257-1.8");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getApiVersion(), "1.8-11.14.0.1257-1.8");
    }

    @Test
    public void testValidMcVersionWithVersionAndBranch()
    {
        // mcversion-version-branch
        this.ext.setVersion("1.8-11.14.0.1257-1.8");
        assertEquals(this.ext.getVersion(), "1.8");
        assertEquals(this.ext.getApiVersion(), "1.8-11.14.0.1257-1.8");
    }

    // Invalid formats

    @Test(expected=RuntimeException.class)
    public void testInvalidBuild()
    {
        // 1.8 build skipped due to 1.7.10
        this.ext.setVersion("11.14.0.1256-1.8");
    }

    @Test(expected=RuntimeException.class)
    public void testInvalidBuildWithMcVersion()
    {
        // 1.8 build skipped due to 1.7.10 (with MC version)
        this.ext.setVersion("1.8-11.14.0.1256-1.8");
    }

    @Test(expected=RuntimeException.class)
    public void testInvalidMcVersion()
    {
        // invalid MC version
        this.ext.setVersion("1.7.10-9.11.1.965");
    }

    @Test(expected=RuntimeException.class)
    public void testInvalidMcVersionWithBranch()
    {
        // invalid MC version (with branch)
        this.ext.setVersion("1.7.10-11.14.0.1257-1.8");
    }

    @Test(expected=RuntimeException.class)
    public void testInvalidBranch()
    {
        // invalid branch
        this.ext.setVersion("1.7.10-11.14.0.1256-1.8");
    }
}

package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedFile;

public class PatcherPlugin extends BasePlugin<PatcherExtension>
{
    @Override
    public void applyPlugin()
    {
        // create and add the namedDomainObjectContainer to the extension object
        getExtension().setProjectContainer(project.container(PatcherProject.class, new PatcherProjectFactory(this)));
        
        makeTasks();
    }
    
    protected void makeTasks()
    {
        
    }
    
    
    protected void createProject(PatcherProject project)
    {
        
    }

    @Override
    protected void addReplaceTokens(PatcherExtension ext)
    {
        // use this? or not use this?
    }
    
    // overlay plugin stuff I dont care about.
    
    @Override
    public boolean canOverlayPlugin()
    {
        return false;
    }
    
    @Override
    public void applyOverlayPlugin()
    {
        // nothing
    }

    @Override
    protected PatcherExtension getOverlayExtension()
    {
        // cant overlay remember?
        return null;
    }
}

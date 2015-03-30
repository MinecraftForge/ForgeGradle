package net.minecraftforge.gradle.dev;

import org.gradle.api.Project;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedFile;

public class PatcherPlugin extends BasePlugin<PatcherExtension>
{

    @Override
    public void applyPlugin()
    {
        getExtension().setProjectContainer(project.container(PatcherProject.class, new PatcherProjectFactory(this)));
        
        
        //project.getExtensions().add("patcherProjects", );
    }
    
    @Override
    protected Class<PatcherExtension> getExtensionClass()
    {
        // TODO Auto-generated method stub
        return PatcherExtension.class;
    }
    
    @Override
    protected DelayedFile getDevJson()
    {
        // TODO Auto-generated method stub
        return null;
    }
    

    @Override
    public void applyOverlayPlugin()
    {
        // nothing
    }

    @Override
    public boolean canOverlayPlugin()
    {
        return false;
    }

    @Override
    protected PatcherExtension getOverlayExtension()
    {
        // cant overlay remember?
        return null;
    }

    @Override
    public String resolve(String pattern, Project project, PatcherExtension exten)
    {
        return super.resolve(pattern, project, exten);
    }

}

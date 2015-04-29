package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.NamedDomainObjectFactory;

public class PatcherProjectFactory implements NamedDomainObjectFactory<PatcherProject>
{
    private final PatcherPlugin plugin;
    
    public PatcherProjectFactory(PatcherPlugin plugin)
    {
        this.plugin = plugin;
    }

    @Override
    public PatcherProject create(String name)
    {
        if (name.equalsIgnoreCase("clean"))
        {
            throw new GradleConfigurationException("You cannot create a project with the name '"+name+"'");
        }
        
        PatcherProject proj = new PatcherProject(name, plugin);
        return proj;
    }
}

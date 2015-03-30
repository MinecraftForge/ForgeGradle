package net.minecraftforge.gradle.dev;

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
        return new PatcherProject(name, plugin);
    }
}

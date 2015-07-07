package net.minecraftforge.gradle.user.patcherUser.generic;

import java.util.List;

import net.minecraftforge.gradle.user.patcherUser.PatcherUserBasePlugin;

public class GenericPatcherUserPlugin extends PatcherUserBasePlugin<GenericPatcherUserExtension>
{
    @Override
    public String getApiGroup(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherGroup();
    }

    @Override
    public String getApiName(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherName();
    }

    @Override
    public String getApiVersion(GenericPatcherUserExtension ext)
    {
        return ext.getPatcherVersion();
    }

    @Override
    public String getUserdevClassifier(GenericPatcherUserExtension ext)
    {
        return ext.getUserdevClassifier();
    }

    @Override
    public String getUserdevExtension(GenericPatcherUserExtension ext)
    {
        return ext.getUserdevExtension();
    }

    @Override
    protected String getClientTweaker(GenericPatcherUserExtension ext)
    {
        return ext.getClientTweaker();
    }

    @Override
    protected String getServerTweaker(GenericPatcherUserExtension ext)
    {
        return ext.getServerTweaker();
    }

    @Override
    protected String getClientRunClass(GenericPatcherUserExtension ext)
    {
        return ext.getClientRunClass();
    }

    @Override
    protected List<String> getClientRunArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedClientRunArgs();
    }

    @Override
    protected String getServerRunClass(GenericPatcherUserExtension ext)
    {
        return ext.getServerRunClass();
    }

    @Override
    protected List<String> getServerRunArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedServerRunArgs();
    }

    @Override
    protected List<String> getClientJvmArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedClientJvmArgs();
    }

    @Override
    protected List<String> getServerJvmArgs(GenericPatcherUserExtension ext)
    {
        return ext.getResolvedServerJvmArgs();
    }
}

package net.minecraftforge.gradle.user.lib;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public class TweakModPlugin extends UserLibBasePlugin<TweakerExtension>
{

    @Override
    public void applyOverlayPlugin()
    {

    }

    @Override
    public boolean canOverlayPlugin()
    {
        return false;
    }

    @Override
    protected String getClientTweaker()
    {
        if(getExtension().getTweaker() == null || "".equals(getExtension().getTweaker()))
            return getExtension().getClientTweaker();
        return getExtension().getTweaker();
    }

    @Override
    protected String getServerTweaker()
    {
        if(getExtension().getTweaker() == null || "".equals(getExtension().getTweaker()))
            return getExtension().getServerTweaker();
        return getExtension().getTweaker();
    }

    @Override
    protected String getClientRunClass()
    {
        return "net.minecraft.launchwrapper.Launch";
    }

    @Override
    protected Iterable<String> getClientRunArgs()
    {
        return ImmutableList.of("--noCoreSearch");
    }

    @Override
    protected String getServerRunClass()
    {
        return "net.minecraft.server.MinecraftServer";
    }

    @Override
    protected Iterable<String> getServerRunArgs()
    {
        return new ArrayList<String>(0);
    }

    @Override
    String actualApiName()
    {
        return "tweaker";
    }

}

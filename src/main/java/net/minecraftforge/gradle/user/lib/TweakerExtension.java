package net.minecraftforge.gradle.user.lib;

import net.minecraftforge.gradle.user.UserExtension;

public class TweakerExtension extends UserExtension
{
    String tweaker = "";
    String clientTweak = "";
    String serverTweak = "";

    public String getTweaker()
    {
        return tweaker;
    }

    public void setTweaker(String tweaker)
    {
        this.tweaker = tweaker;
    }

    public String getClientTweaker()
    {
        return clientTweak;
    }

    public void setClientTweaker(String clientTweak)
    {
        this.clientTweak = clientTweak;
    }

    public String getServerTweaker()
    {
        return serverTweak;
    }

    public void setServerTweaker(String serverTweak)
    {
        this.serverTweak = serverTweak;
    }

    public TweakerExtension(TweakModPlugin plugin)
    {
        super(plugin);
    }
}

package net.minecraftforge.gradle.user.tweakers;

public class ServerTweaker extends TweakerPlugin
{
    @Override
    boolean isClient()
    {
        return false;
    }

}

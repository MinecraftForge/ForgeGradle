package net.minecraftforge.gradle.user.tweakers;

public class ClientTweaker extends TweakerPlugin
{
    @Override
    boolean isClient()
    {
        return true;
    }

}

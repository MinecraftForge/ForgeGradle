package net.minecraftforge.gradle.user.tweakers;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserExtension;

public class TweakerExtension extends UserExtension
{
    private Object tweakClass;
    
    public TweakerExtension(TweakerPlugin plugin)
    {
        super(plugin);
    }

    public String getTweakClass()
    {
        return Constants.resolveString(tweakClass);
    }

    public void setTweakClass(String tweakClass)
    {
        this.tweakClass = tweakClass;
    }
}

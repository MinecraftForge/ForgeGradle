package net.minecraftforge.gradle.user.tweakers;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserBaseExtension;

public class TweakerExtension extends UserBaseExtension
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

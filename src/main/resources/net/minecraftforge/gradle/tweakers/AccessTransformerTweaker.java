package net.minecraftforge.gradle.tweakers;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class AccessTransformerTweaker implements ITweaker
{

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile)
    {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader)
    {
        // so I can get it in the right ClassLaoder
        classLoader.registerTransformer("net.minecraftforge.gradle.GradleStartCommon$AccessTransformerTransformer");
    }

    @Override
    public String getLaunchTarget()
    {
        // if it gets here... something went terribly wrong..
        return null;
    }

    @Override
    public String[] getLaunchArguments()
    {
        // if it gets here... something went terribly wrong.
        return new String[0];
    }

}

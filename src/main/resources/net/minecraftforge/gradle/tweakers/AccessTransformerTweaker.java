package net.minecraftforge.gradle.tweakers;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

public class AccessTransformerTweaker implements ITweaker
{
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader)
    {
        // so I can get it in the right ClassLaoder
        classLoader.registerTransformer("net.minecraftforge.gradle.GradleStartCommon$AccessTransformerTransformer");
    }

    //@formatter:off
    @Override public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) { }
    @Override public String getLaunchTarget() { return null; }
    @Override public String[] getLaunchArguments() { return new String[0]; }
}

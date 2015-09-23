package net.minecraftforge.gradle.user.tweakers;

import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.MCP_PATCHES_CLIENT;
import static net.minecraftforge.gradle.common.Constants.TASK_DL_CLIENT;

public class ClientTweaker extends TweakerPlugin
{
    @Override
    protected String getJarName()
    {
        return "minecraft";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_CLIENT_FRESH), TASK_DL_CLIENT, delayedFile(MCP_PATCHES_CLIENT));
    }

    @Override
    protected boolean hasServerRun()
    {
        return false;
    }

    @Override
    protected boolean hasClientRun()
    {
        return true;
    }

    @Override
    protected String getClientRunClass(TweakerExtension ext)
    {
        return "net.minecraft.launchwrapper.Launch";
    }

}

package net.minecraftforge.gradle.user.patch;

import static net.minecraftforge.gradle.user.patch.UserPatchConstants.*;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;

public class FmlUserPlugin extends UserPatchBasePlugin
{
    @Override
    public String getApiName()
    {
        return "fml";
    }
    
    @Override
    protected String getApiGroup()
    {
        return "cpw.mods";
    }

    @Override
    protected void configureDeobfuscation(ProcessJarTask task)
    {
        task.addTransformerClean(delayedFile(FML_AT));       
    }
    
    @Override
    protected void configurePatching(ProcessSrcJarTask patch)
    {
        patch.addStage("fml", delayedFile(FML_PATCHES_ZIP), delayedFile(SRC_DIR), delayedFile(RES_DIR));
    }
    
    @Override
    protected void doVersionChecks(int buildNumber)
    {
        if (buildNumber < 883)
            throw new IllegalArgumentException("ForgeGradle 1.2 only works for FML versions 7.2.132.882+");
    }
}

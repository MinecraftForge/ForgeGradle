package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ProcessJarTask;

public class FmlUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(UserConstants.FML_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(UserConstants.FML_DEOBF_MCP));
        }
        
        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(UserConstants.FML_DEOBF_SRG));
        }

        /*
            setupDevWorkspace
              Downloads:  CHECK
                notch->srg Srg File
                Exceptor info
                merge config
                FML/Forge src file
                Decomp:
                  Mapping Info, with full comments
                  MCP patches
                  FML/Forge patches
                No Decomp:
                  Mapping Info (fields/methods/params.csv minus comments?)
                  Binpatches
                  Javadoc jar
                    
              Process:
                Download MC/Server
                Merged
                No Decomp:
                  Apply BinPatches
                  TODO: add in FML classes
                  Apply mapping to notch->srg to get notch->mapped
                  Apply notch->mapped to merged
                  Resulting jar is 'minecraft.jar'
                  Link Javadocs to ',inecraft.jar'
                  Link jar containing FML/Forge's src files to 'minecraft.jar'
                Decomp:
                  Full MCP decompile process
                     should result in: minecraft.jar {recompiled version fo the fully mapped source with commends and the like} 
                     minecraft-source.jar a jar of the decompiled code, the source shoukd bnever be linked in the final workspace
         */
    }

    @Override
    public void afterEvaluate()
    {
        project.getDependencies().add(UserConstants.CONFIG_USERDEV, "cpw.mods:fml:" + getExtension().getApiVersion() + ":userdev");
        
        super.afterEvaluate();
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformer(delayedFile(UserConstants.FML_AT));
    }
    
    @Override
    protected DelayedFile getBinPatchOut()
    {
        return delayedFile(UserConstants.FML_BINPATCHED);
    }

    @Override
    protected DelayedFile getDecompOut()
    {
        return delayedFile(UserConstants.FML_DECOMP);
    }
}

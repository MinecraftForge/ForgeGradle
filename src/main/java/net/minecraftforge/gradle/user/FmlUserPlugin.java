package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;

public class FmlUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ApplyBinPatchesTask binTask = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
        {
            binTask.setInJar(delayedFile(Constants.JAR_MERGED));
            binTask.setOutJar(delayedFile(UserConstants.FML_BINPATCHED));
            binTask.setPatches(delayedFile(UserConstants.BINPATCHES));
            binTask.setClassesJar(delayedFile(UserConstants.BINARIES_JAR));
            binTask.dependsOn("mergeJars");
        }

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.dependsOn(binTask);
            procTask.setInJar(delayedFile(UserConstants.FML_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(UserConstants.FML_DEOBF_MCP));
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
        super.afterEvaluate();

        project.getDependencies().add(UserConstants.CONFIG, project.files(delayedFile(UserConstants.FML_DEOBF_MCP).call()));
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformer(delayedFile(UserConstants.FML_AT));
    }
}

package net.minecraftforge.gradle.user;

import org.gradle.api.Task;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;

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
        
        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("remapJar");
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

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        DelayedFile fmled = delayedFile( isClean ? UserConstants.FML_FMLED : Constants.DECOMP_FMLED);
        DelayedFile remapped = delayedFile( isClean ? UserConstants.FML_REMAPPED : Constants.DECOMP_REMAPPED);
        
        PatchJarTask fmlPatches = makeTask("doFmlPatches", PatchJarTask.class);
        {
            fmlPatches.dependsOn("decompile");
            fmlPatches.setInJar(decompOut);
            fmlPatches.setOutJar(fmled);
            fmlPatches.setInPatches(delayedFile(UserConstants.FML_PATCHES_ZIP));
        }
        
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn("doFmlPatches");
            remap.setInJar(fmled);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(UserConstants.FIELD_CSV));
            remap.setMethodsCsv(delayedFile(UserConstants.METHOD_CSV));
            remap.setParamsCsv(delayedFile(UserConstants.PARAM_CSV));
        }
    }
}

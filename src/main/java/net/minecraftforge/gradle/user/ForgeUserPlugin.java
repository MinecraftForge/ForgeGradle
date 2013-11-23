package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.CONFIG_API_JAVADOCS;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_USERDEV;

import java.io.File;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;

import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

public class ForgeUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();
        
        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(UserConstants.FORGE_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(UserConstants.FORGE_DEOBF_MCP));
        }
        
        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(UserConstants.FORGE_DEOBF_SRG));
        }
        
        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("addForgeSources");
    }

    @Override
    public void afterEvaluate()
    {
        String depBase = "net.minecraftforge:forge:" + getExtension().getApiVersion();
        project.getDependencies().add(CONFIG_USERDEV,      depBase + ":userdev");
        project.getDependencies().add(CONFIG_API_JAVADOCS, depBase + ":javadoc@zip");

        super.afterEvaluate();
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformer(delayedFile(UserConstants.FML_AT));
        task.addTransformer(delayedFile(UserConstants.FORGE_AT));
    }
    
    @Override
    protected DelayedFile getBinPatchOut()
    {
        return delayedFile(UserConstants.FORGE_BINPATCHED);
    }
    
    @Override
    protected DelayedFile getDecompOut()
    {
        return delayedFile(UserConstants.FORGE_DECOMP);
    }

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        DelayedFile fmled = delayedFile(isClean ? UserConstants.FORGE_FMLED : Constants.DECOMP_FMLED);
        DelayedFile fmlInjected = delayedFile(isClean ? UserConstants.FORGE_FMLINJECTED : Constants.DECOMP_FMLINJECTED);
        DelayedFile remapped = delayedFile(isClean ? UserConstants.FORGE_REMAPPED : Constants.DECOMP_REMAPPED);
        DelayedFile forged = delayedFile(isClean ? UserConstants.FORGE_FORGED : Constants.DECOMP_FORGED);
        DelayedFile forgeInjected = delayedFile(isClean ? UserConstants.FORGE_FORGEINJECTED : Constants.DECOMP_FORGEINJECTED);
        
        PatchJarTask fmlPatches = makeTask("doFmlPatches", PatchJarTask.class);
        {
            fmlPatches.dependsOn("decompile");
            fmlPatches.setInJar(decompOut);
            fmlPatches.setOutJar(fmled);
            fmlPatches.setInPatches(delayedFile(UserConstants.FML_PATCHES_ZIP));
        }
        
        Zip inject = makeTask("addFmlSources", Zip.class);
        {
            inject.dependsOn("doFmlPatches");
            inject.from(fmled.toZipTree());
            //inject.from(delayedFile(src.))  get the source!!!!
            
            File injectFile = fmlInjected.call();
            inject.setDestinationDir(injectFile.getParentFile());
            inject.setArchiveName(injectFile.getName());
        }
        
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn("addFmlSources");
            remap.setInJar(fmled);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(UserConstants.FIELD_CSV));
            remap.setMethodsCsv(delayedFile(UserConstants.METHOD_CSV));
            remap.setParamsCsv(delayedFile(UserConstants.PARAM_CSV));
        }
        
        PatchJarTask forgePatches = makeTask("doForgePatches", PatchJarTask.class);
        {
            forgePatches.dependsOn("remapJar");
            forgePatches.setInJar(remapped);
            forgePatches.setOutJar(forged);
            forgePatches.setInPatches(delayedFile(UserConstants.FORGE_PATCHES_ZIP));
        }
        
        inject = makeTask("addForgeSources", Zip.class);
        {
            inject.dependsOn("doForgePatches");
            inject.from(forged.toZipTree());
            //inject.from(delayedFile(src.))  get the source!!!!
            
            File injectFile = forgeInjected.call();
            inject.setDestinationDir(injectFile.getParentFile());
            inject.setArchiveName(injectFile.getName());
        }
    }
}

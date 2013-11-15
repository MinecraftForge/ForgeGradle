package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;

public class ForgeUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ApplyBinPatchesTask binTask = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
        {
            binTask.setInJar(delayedFile(Constants.JAR_MERGED));
            binTask.setOutJar(delayedFile(UserConstants.FORGE_BINPATCHED));
            binTask.setPatches(delayedFile(UserConstants.BINPATCHES));
            binTask.setClassesJar(delayedFile(UserConstants.BINARIES_JAR));
            binTask.dependsOn("mergeJars");
        }

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.dependsOn(binTask);
            procTask.setInJar(delayedFile(UserConstants.FORGE_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(UserConstants.FORGE_DEOBF_MCP));
        }
    }

    @Override
    public void afterEvaluate()
    {
        project.getDependencies().add(UserConstants.CONFIG_USERDEV, "net.minecraftforge:forge:" + getExtension().getApiVersion() + ":userdev");
        super.afterEvaluate();

        project.getDependencies().add(UserConstants.CONFIG, project.files(delayedFile(UserConstants.FORGE_DEOBF_MCP).call()));
        fixEclipseProject(UserConstants.ECLIPSE_LOCATION);
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformer(delayedFile(UserConstants.FML_AT));
        task.addTransformer(delayedFile(UserConstants.FORGE_AT));
    }
}

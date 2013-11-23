package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.CONFIG_API_JAVADOCS;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_USERDEV;
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
            binTask.setResources(delayedFileTree(UserConstants.RES_DIR));
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
}

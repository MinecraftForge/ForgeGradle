package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.CONFIG_API_JAVADOCS;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_USERDEV;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ProcessJarTask;

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
    protected void doPostDecompTasks(boolean isClean, String decompTaskName)
    {
        
    }
}

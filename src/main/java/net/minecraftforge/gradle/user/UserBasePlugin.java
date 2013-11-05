package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_SRG;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_EXC;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_SRG;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;

public abstract class UserBasePlugin<K extends UserExtension> extends BasePlugin<K> // TODO: change this to the actual extension class eventually, the one specific to the FML User plugin
{

    @Override
    public void applyPlugin()
    {
        // TODO tasks....
    }
    
    protected abstract Class<K> getExtensionClass(); // forces impl later.

    @Override
    protected String getDevJson()
    {
        // TODO what should we put here?
        return null;
    }
    
    private void makeJarTasks()
    {
        MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
        {
            task.setClient(delayedFile(JAR_CLIENT_FRESH));
            task.setServer(delayedFile(JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(JAR_MERGED));
            task.setMergeCfg(delayedFile(UserConstants.MERGE_CFG));
            task.dependsOn("downloadClient", "downloadServer");
        }

        // TODO: FIXING NECESSARY!
        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(JAR_MERGED));
            task2.setExceptorJar(delayedFile(EXCEPTOR));
            task2.setOutJar(delayedFile(JAR_SRG));
            task2.setSrg(delayedFile(PACKAGED_SRG));
            task2.setExceptorCfg(delayedFile(PACKAGED_EXC));
            //task2.addTransformer(delayedFile(FML_COMMON + "/fml_at.cfg"));  need the AT
            task2.dependsOn("downloadMcpTools", "fixMappings", "mergeJars");
        }
    }

}

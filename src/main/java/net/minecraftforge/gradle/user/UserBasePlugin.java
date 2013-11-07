package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_SRG;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_EXC;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_SRG;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Delete;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;

public abstract class UserBasePlugin extends BasePlugin<UserExtension> implements IDelayedResolver<UserExtension>
{

    @Override
    public void applyPlugin()
    {
        makeJarTasks();
        
        configureCIWorkspace();
        
        // lifecycle tasks
        
        Task task = makeTask("setupCIWorkspace", DefaultTask.class);
        addSetupCiTaskDeps(task);
        
        task = makeTask("setupDevWorkspace", DefaultTask.class);
        addSetupDevTaskDeps(task);
        
        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        addSetupDecompTaskDeps(task);
        
        // deleteTask
        Delete del = makeTask("cleanMc", Delete.class);
        {
            del.delete(delayedFile("{BASE_DIR}"));
        }
    }
    
    protected abstract void addSetupCiTaskDeps(Task task);
    
    protected abstract void addSetupDevTaskDeps(Task task);
    
    protected abstract void addSetupDecompTaskDeps(Task task);
    
    protected Class<UserExtension> getExtensionClass()
    {
        return UserExtension.class;
    }

    @Override
    protected String getDevJson()
    {
        return DelayedBase.resolve(UserConstants.JSON, project, this);
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

        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(JAR_MERGED));
            task2.setExceptorJar(delayedFile(EXCEPTOR));
            task2.setOutJar(delayedFile(JAR_SRG));
            task2.setSrg(delayedFile(PACKAGED_SRG));
            task2.setExceptorCfg(delayedFile(PACKAGED_EXC));
            //task2.addTransformer(delayedFile(FML_COMMON + "/fml_at.cfg"));
            // TODO closure that aggregates all the stuff.
            task2.dependsOn("downloadMcpTools", "fixMappings", "mergeJars");
        }
    }
    
    private void configureCIWorkspace()
    {
        // TODO
    }
    
    @Override
    public String resolve(String pattern, Project project, UserExtension exten)
    {
        pattern = pattern.replace("{BASE_DIR}", exten.getBaseDir());
        return pattern;
    }
    
    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path, this); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path, this); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path, this); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true, this); }
}

package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;

import argo.jdom.JsonRootNode;

public abstract class UserBasePlugin extends BasePlugin<UserExtension> implements IDelayedResolver<UserExtension>
{
    private UserJson json = new UserJson(delayedFile(UserConstants.JSON).call());

    @Override
    public void applyPlugin()
    {
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("maven");

        configureDeps();

        tasks();

        configureCIWorkspace();

        // lifecycle tasks

        Task task = makeTask("setupCIWorkspace", DefaultTask.class);
        addSetupCiTaskDeps(task);

        task = makeTask("setupDevWorkspace", DefaultTask.class);
        addSetupDevTaskDeps(task);

        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        addSetupDecompTaskDeps(task);
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
        return DelayedBase.resolve(UserConstants.JSON, project);
    }

    private void tasks()
    {
        MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
        {
            task.setClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task.setServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(Constants.JAR_MERGED));
            task.setMergeCfg(delayedFile(UserConstants.MERGE_CFG));
            task.dependsOn("downloadClient", "downloadServer", "extractUserDev");
        }

        final ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            task2.setOutCleanJar(delayedFile(Constants.JAR_SRG));
            task2.setSrg(delayedFile(Constants.PACKAGED_SRG));
            addATs(task2);
            task2.setExceptorCfg(delayedFile(Constants.PACKAGED_EXC));
            task2.dependsOn("downloadMcpTools", "mergeJars", "applyBinPatches");
        }
    }
    
    protected abstract void addATs(ProcessJarTask task);

    private void configureDeps()
    {
        // create configs
        project.getConfigurations().create(UserConstants.CONFIG_USERDEV);
        project.getConfigurations().create(UserConstants.CONFIG_NATIVES);
        project.getConfigurations().create(UserConstants.CONFIG);
        
        // special userDev stuff
        final ExtractTask extracter = makeTask("extractUserDev", ExtractTask.class);
        extracter.into(delayedFile(UserConstants.PACK_DIR));
        extracter.doLast(new Action<Task>() {
            @Override
            public void execute(Task arg0)
            {
                json.apply(project, UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
            }
        });
    }
    
    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        if (delayedFile(UserConstants.JSON).call().exists())
        {
            json.apply(project, UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
        }
        
        project.getDependencies().add(UserConstants.CONFIG_USERDEV, getExtension().getNotation() + ":userdev");
        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(UserConstants.CONFIG_USERDEV).getSingleFile().getAbsolutePath()));
        
        //FileCollection files = project.files(delayedString(UserConstants.JAVADOC_JAR).call(), delayedString(UserConstants.ASTYLE_CFG).call());
        //project.getDependencies().add(UserConstants.CONFIG, files);
        //project.getDependencies().add(paramString, paramObject)
    }

    private void configureCIWorkspace()
    {
        // TODO
    }
    
    @Override
    public String resolve(String pattern, Project project, UserExtension exten)
    {
        pattern = pattern.replace("{API_VERSION}", exten.getApiVersion());
        return pattern;
    }
    
    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path, this); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path, this); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path, this); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true, this); }
}

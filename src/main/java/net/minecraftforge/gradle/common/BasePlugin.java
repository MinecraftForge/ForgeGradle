package net.minecraftforge.gradle.common;

import static net.minecraftforge.gradle.common.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_JENKINS;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;
import static net.minecraftforge.gradle.common.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.common.Constants.MCP_URL;
import static net.minecraftforge.gradle.common.Constants.MC_JAR_URL;
import static net.minecraftforge.gradle.common.Constants.MC_SERVER_URL;
import groovy.lang.Closure;

import java.io.File;
import java.util.HashMap;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.ObtainMcpStuffTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>
{
    protected Project project;

    @Override
    @SuppressWarnings("serial")
    public final void apply(Project arg)
    {
        project = arg;
        
        project.getLogger().lifecycle("**********************************");
        project.getLogger().lifecycle("**MMMMMMM*POWERED BY MCP**********");
        project.getLogger().lifecycle("**M**M**M*POWERED BY MCP**********");
        project.getLogger().lifecycle("  M     M POWERED BY MCP**********");
        project.getLogger().lifecycle("*********************************");
        
        project.getExtensions().create(EXT_NAME_MC, getExtensionClass(), project);
        project.getExtensions().create(EXT_NAME_JENKINS, JenkinsExtension.class, project);
        
        
        addMavenRepo("forge2", "http://files.minecraftforge.net/maven2");
        addMavenRepo("forge", "http://files.minecraftforge.net/maven");
        project.getRepositories().mavenCentral();
        
        project.afterEvaluate(new Closure<Object>(project, this){
            @Override
            public Object call()
            {   
                afterEvaluate();
                return null;
            }
            
            @Override public Object call(Object obj) { return call(); }
            
            @Override public Object call(Object... obj){ return call(); }
        });
        
        makeObtainTasks();
        
        // at last....
        applyPlugin();
    }
    
    public abstract void applyPlugin();
    
    protected abstract String getDevJson();
    
    public void afterEvaluate() {}
    
    private void makeObtainTasks()
    {
        // download tasks
        DownloadTask task;

        task = makeTask("downloadClient", DownloadTask.class);
        {
            task.setOutput(delayedFile(JAR_CLIENT_FRESH));
            task.setUrl(delayedString(MC_JAR_URL));
        }

        task = makeTask("downloadServer", DownloadTask.class);
        {
            task.setOutput(delayedFile(JAR_SERVER_FRESH));
            task.setUrl(delayedString(MC_SERVER_URL));
        }
        
        ObtainMcpStuffTask mcpTask = makeTask("downloadMcpTools", ObtainMcpStuffTask.class);
        {
            mcpTask.setMcpUrl(delayedString(MCP_URL));
            mcpTask.setFfJar(delayedFile(FERNFLOWER));
            mcpTask.setInjectorJar(delayedFile(EXCEPTOR));
        }
    }
    
    /**
     * This extension object will have the name "minecraft"
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Class<K> getExtensionClass(){ return (Class<K>) BaseExtension.class; }
    
    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants.EXT_NAME_MC
     */
    @SuppressWarnings("unchecked")
    public final K getExtension()
    {
        return (K) project.getExtensions().getByName(Constants.EXT_NAME_MC);
    }
    
    public DefaultTask makeTask(String name)
    {
        return makeTask(name, DefaultTask.class);
    }
    
    public <T extends Task> T makeTask(String name, Class<T> type)
    {
        return makeTask(project, name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Task> T makeTask(Project proj, String name, Class<T> type)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("name", name);
        map.put("type", type);
        return (T) proj.task(map, name);
    }

    public static Project getProject(File buildFile, Project parent)
    {
        Project project = ProjectBuilder.builder()
                .withProjectDir(buildFile.getParentFile())
                .withName(buildFile.getParentFile().getName())
                .withParent(parent)
                .build();

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("from", buildFile.getName());

        project.apply(map);

        return project;
    }
    
    public void applyExternalPlugin(String plugin)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("plugin", plugin);
        project.apply(map);
    }
    
    public void addMavenRepo(final String name, final String url)
    {
        project.getRepositories().maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository repo)
            {
                repo.setName(name);
                repo.setUrl(url);
            }
        });
    }

    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true); }

}

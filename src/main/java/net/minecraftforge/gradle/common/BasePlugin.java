package net.minecraftforge.gradle.common;

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
    public static Project project;

    @Override
    public final void apply(Project arg)
    {
        project = arg;

        project.getLogger().lifecycle("**************************");
        project.getLogger().lifecycle("Powered By MCP Data:      ");
        project.getLogger().lifecycle(" http://mcp.ocean-labs.de/ ");
        project.getLogger().lifecycle(" Searge, ProfMobius, Fesh0r, ");
        project.getLogger().lifecycle(" R4wk, ZeuX, IngisKahn ");
        project.getLogger().lifecycle("**************************");

        project.getExtensions().create(Constants.EXT_NAME_MC, getExtensionClass(), project);
        project.getExtensions().create(Constants.EXT_NAME_JENKINS, JenkinsExtension.class, project);


        addMavenRepo("forge", "http://files.minecraftforge.net/maven");
        project.getRepositories().mavenCentral();
        addMavenRepo("minecraft", "http://s3.amazonaws.com/Minecraft.Download/libraries");

        project.afterEvaluate(new Action<Project>(){
            @Override
            public void execute(Project project)
            {
                afterEvaluate();
            }
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
            task.setOutput(delayedFile(Constants.JAR_CLIENT_FRESH));
            task.setUrl(delayedString(Constants.MC_JAR_URL));
        }

        task = makeTask("downloadServer", DownloadTask.class);
        {
            task.setOutput(delayedFile(Constants.JAR_SERVER_FRESH));
            task.setUrl(delayedString(Constants.MC_SERVER_URL));
        }

        ObtainMcpStuffTask mcpTask = makeTask("downloadMcpTools", ObtainMcpStuffTask.class);
        {
            mcpTask.setMcpUrl(delayedString(Constants.MCP_URL));
            mcpTask.setFfJar(delayedFile(Constants.FERNFLOWER));
            mcpTask.setInjectorJar(delayedFile(Constants.EXCEPTOR));
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

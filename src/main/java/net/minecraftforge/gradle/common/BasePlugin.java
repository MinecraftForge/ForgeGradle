package net.minecraftforge.gradle.common;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import net.minecraftforge.gradle.FileLogListenner;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.Version;
import net.minecraftforge.gradle.tasks.DownloadAssetsTask;
import net.minecraftforge.gradle.tasks.ObtainFernFlowerTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import com.google.common.base.Throwables;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>, IDelayedResolver<K>
{
    public Project    project;
    @SuppressWarnings("rawtypes")
    public BasePlugin otherPlugin;
    public Version    version;
    public AssetIndex assetIndex;

    @SuppressWarnings("rawtypes")
    @Override
    public final void apply(Project arg)
    {
        project = arg;

        // search for overlays..
        for (Plugin p : project.getPlugins())
        {
            if (p instanceof BasePlugin && p != this)
            {
                if (canOverlayPlugin())
                {
                    project.getLogger().info("Applying Overlay");
                    
                    // found another BasePlugin thats already applied.
                    // do only overlay stuff and return;
                    otherPlugin = (BasePlugin) p;
                    applyOverlayPlugin();
                    return;
                }
                else
                {
                    throw new RuntimeException("Seems you are trying to apply 2 ForgeGradle plugins that are not designed to overlay... Fix your buildscripts.");
                }
            }
        }

        // logging
        FileLogListenner listener = new FileLogListenner(project.file(Constants.LOG));
        project.getLogging().addStandardOutputListener(listener);
        project.getLogging().addStandardErrorListener(listener);
        project.getGradle().addBuildListener(listener);

        if (project.getBuildDir().getAbsolutePath().contains("!"))
        {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // extension objects
        project.getExtensions().create(Constants.EXT_NAME_MC, getExtensionClass(), this);
        project.getExtensions().create(Constants.EXT_NAME_JENKINS, JenkinsExtension.class, project);

        // repos
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addMavenRepo(proj, "forge", Constants.FORGE_MAVEN);
                proj.getRepositories().mavenCentral();
                addMavenRepo(proj, "minecraft", Constants.LIBRARY_URL);
            }
        });

        // after eval
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                afterEvaluate();

                try
                {
                    if (version != null)
                    {
                        File index = delayedFile(Constants.ASSETS + "/indexes/" + version.getAssets() + ".json").call();
                        if (index.exists())
                            parseAssetIndex();
                    }
                }
                catch (Exception e)
                {
                    Throwables.propagate(e);
                }

                finalCall();
            }
        });

        // some default tasks
        makeObtainTasks();

        // at last, apply the child plugins
        applyPlugin();
    }

    public abstract void applyPlugin();

    public abstract void applyOverlayPlugin();

    /**
     * return true if this plugin can be applied over another BasePlugin.
     */
    public abstract boolean canOverlayPlugin();

    protected abstract DelayedFile getDevJson();

    private static boolean displayBanner = true;

    public void afterEvaluate()
    {
        if (!displayBanner)
            return;
        project.getLogger().lifecycle("****************************");
        project.getLogger().lifecycle(" Powered By MCP:            ");
        project.getLogger().lifecycle(" http://mcp.ocean-labs.de/  ");
        project.getLogger().lifecycle(" Searge, ProfMobius, Fesh0r,");
        project.getLogger().lifecycle(" R4wk, ZeuX, IngisKahn      ");
        project.getLogger().lifecycle(delayedString(" MCP Data version : {MCP_VERSION}").call());
        project.getLogger().lifecycle("****************************");
        displayBanner = false;
    }

    public void finalCall()
    {
    }

    @SuppressWarnings("serial")
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

        ObtainFernFlowerTask mcpTask = makeTask("downloadMcpTools", ObtainFernFlowerTask.class);
        {
            mcpTask.setMcpUrl(delayedString(Constants.MCP_URL));
            mcpTask.setFfJar(delayedFile(Constants.FERNFLOWER));
        }

        DownloadTask getAssetsIndex = makeTask("getAssetsIndex", DownloadTask.class);
        {
            getAssetsIndex.setUrl(delayedString(Constants.ASSETS_INDEX_URL));
            getAssetsIndex.setOutput(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json"));
            getAssetsIndex.setDoesCache(false);

            getAssetsIndex.doLast(new Action<Task>() {
                public void execute(Task task)
                {
                    try
                    {
                        parseAssetIndex();
                    }
                    catch (Exception e)
                    {
                        Throwables.propagate(e);
                    }
                }
            });

            getAssetsIndex.getOutputs().upToDateWhen(new Closure<Boolean>(this, null) {
                public Boolean call(Object... obj)
                {
                    return false;
                }
            });
        }

        DownloadAssetsTask assets = makeTask("getAssets", DownloadAssetsTask.class);
        {
            assets.setAssetsDir(delayedFile(Constants.ASSETS));
            assets.setIndex(getAssetIndexClosure());
            assets.dependsOn("getAssetsIndex");
        }

        Delete clearCache = makeTask("cleanCache", Delete.class);
        {
            clearCache.delete(delayedFile("{CACHE_DIR}/minecraft"));
            clearCache.setGroup("ForgeGradle");
            clearCache.setDescription("Cleares the ForgeGradle cache. DONT RUN THIS unless you want a fresh start, or the dev tells you to.");
        }
    }

    public void parseAssetIndex() throws JsonSyntaxException, JsonIOException, IOException
    {
        assetIndex = JsonFactory.loadAssetsIndex(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json").call());
    }

    @SuppressWarnings("serial")
    public Closure<AssetIndex> getAssetIndexClosure()
    {
        return new Closure<AssetIndex>(this, null) {
            public AssetIndex call(Object... obj)
            {
                return getAssetIndex();
            }
        };
    }

    public AssetIndex getAssetIndex()
    {
        return assetIndex;
    }

    /**
     * This extension object will have the name "minecraft"
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Class<K> getExtensionClass()
    {
        return (Class<K>) BaseExtension.class;
    }

    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants.EXT_NAME_MC
     */
    @SuppressWarnings("unchecked")
    public final K getExtension()
    {
        if (otherPlugin != null && canOverlayPlugin())
            return getOverlayExtension();
        else
            return (K) project.getExtensions().getByName(Constants.EXT_NAME_MC);
    }
    
    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants.EXT_NAME_MC
     */
    protected abstract K getOverlayExtension();

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
        ProjectBuilder builder = ProjectBuilder.builder();
        if (buildFile != null)
        {
            builder = builder.withProjectDir(buildFile.getParentFile())
                    .withName(buildFile.getParentFile().getName());
        }
        else
        {
            builder = builder.withProjectDir(new File("."));
        }

        if (parent != null)
        {
            builder = builder.withParent(parent);
        }

        Project project = builder.build();

        if (buildFile != null)
        {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("from", buildFile.getAbsolutePath());

            project.apply(map);
        }

        return project;
    }

    public void applyExternalPlugin(String plugin)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("plugin", plugin);
        project.apply(map);
    }

    public MavenArtifactRepository addMavenRepo(Project proj, final String name, final String url)
    {
        return proj.getRepositories().maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository repo)
            {
                repo.setName(name);
                repo.setUrl(url);
            }
        });
    }

    public FlatDirectoryArtifactRepository addFlatRepo(Project proj, final String name, final Object... dirs)
    {
        return proj.getRepositories().flatDir(new Action<FlatDirectoryArtifactRepository>() {
            @Override
            public void execute(FlatDirectoryArtifactRepository repo)
            {
                repo.setName(name);
                repo.dirs(dirs);
            }
        });
    }

    @Override
    public String resolve(String pattern, Project project, K exten)
    {
        if (version != null)
            pattern = pattern.replace("{ASSET_INDEX}", version.getAssets());
        return pattern;
    }

    protected DelayedString delayedString(String path)
    {
        return new DelayedString(project, path, this);
    }

    protected DelayedFile delayedFile(String path)
    {
        return new DelayedFile(project, path, this);
    }

    protected DelayedFileTree delayedFileTree(String path)
    {
        return new DelayedFileTree(project, path, this);
    }

    protected DelayedFileTree delayedZipTree(String path)
    {
        return new DelayedFileTree(project, path, true, this);
    }

}

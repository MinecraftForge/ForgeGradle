package net.minecraftforge.gradle.common;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;

import net.minecraftforge.gradle.FileLogListenner;
import net.minecraftforge.gradle.common.version.AssetIndex;
import net.minecraftforge.gradle.common.version.Version;
import net.minecraftforge.gradle.common.version.json.JsonFactory;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.MCVersionManifest;
import net.minecraftforge.gradle.json.version.VersionToString;
import net.minecraftforge.gradle.tasks.DownloadAssetsTask;
import net.minecraftforge.gradle.tasks.ObtainFernFlowerTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;

import net.minecraftforge.gradle.tasks.abstractutil.EtagDownloadTask;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import com.google.common.base.Throwables;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>, IDelayedResolver<K>
{
    public Project    project;
    public Version    version;
    public AssetIndex assetIndex;

    @Override
    public final void apply(Project arg)
    {
        project = arg;
        
        // logging
        FileLogListenner listenner = new FileLogListenner(project.file(Constants.LOG));
        project.getLogging().addStandardOutputListener(listenner);
        project.getLogging().addStandardErrorListener(listenner);
        project.getGradle().addBuildListener(listenner);

        if (project.getBuildDir().getAbsolutePath().contains("!"))
        {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // extension objects
        project.getExtensions().create(Constants.EXT_NAME_MC, getExtensionClass(), project);
        project.getExtensions().create(Constants.EXT_NAME_JENKINS, JenkinsExtension.class, project);

        // repos
        project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                addMavenRepo(proj, "forge", "http://files.minecraftforge.net/maven");
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

    protected abstract String getDevJson();

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
    
    public void finalCall() {}

    @SuppressWarnings("serial")
    private void makeObtainTasks()
    {
        // download tasks
        DownloadTask task;

        EtagDownloadTask etagDlTask;
        etagDlTask = makeTask("getVersionJsonIndex", EtagDownloadTask.class);
        {
            etagDlTask.setUri(delayedString(Constants.MC_JSON_INDEX_URL));
            etagDlTask.setFile(delayedFile(Constants.VERSION_JSON_INDEX));
            etagDlTask.setDieWithError(false);
        }

        etagDlTask = makeTask("getVersionJson", EtagDownloadTask.class);
        {
            class GetVersionJsonUrl extends DelayedString {
                public GetVersionJsonUrl() {
                    super(BasePlugin.this.project, "");
                }

                @Override
                public String call() {
                    try {
                        MCVersionManifest manifest = JsonFactory.loadMCVersionManifest(delayedFile(Constants.VERSION_JSON_INDEX).call());
                        MCVersionManifest.Version version = manifest.findVersion(delayedString("{MC_VERSION}").call());
                        return version.url;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            etagDlTask.dependsOn("getVersionJsonIndex");
            etagDlTask.getInputs().file(delayedFile(Constants.VERSION_JSON_INDEX));
            etagDlTask.setUri(new GetVersionJsonUrl());
            etagDlTask.setFile(delayedFile(Constants.VERSION_JSON));
            etagDlTask.setDieWithError(false);
            //TODO: this is not necessary?
            etagDlTask.doLast(new Action<Task>() { // normalizes to linux endings
                @Override
                public void execute(Task task) {
                    try {
                        File json = delayedFile(Constants.VERSION_JSON).call();
                        if (!json.exists())
                            return;

                        List<String> lines = Files.readAllLines(json.toPath());
                        StringBuilder buf = new StringBuilder();
                        for (String line : lines) {
                            buf.append(line).append('\n');
                        }
                        Files.write(json.toPath(), buf.toString().getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }

        class GetDataFromJson extends DelayedString {
            private final VersionToString function;

            public GetDataFromJson(VersionToString function) {
                super(BasePlugin.this.project, "");
                this.function = function;
            }

            @Override
            public String call() {
                try {
                    Version manifest = JsonFactory.loadVersion(delayedFile(Constants.VERSION_JSON).call());
                    return function.apply(manifest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        task = makeTask("downloadClient", DownloadTask.class);
        {
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_CLIENT_FRESH));
            task.setUrl(new GetDataFromJson(new VersionToString() {
                @Override
                public String apply(Version json) {
                    return json.downloads.client.url;
                }
            }));


        }

        task = makeTask("downloadServer", DownloadTask.class);
        {
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_SERVER_FRESH));
            task.setUrl(new GetDataFromJson(new VersionToString() {
                @Override
                public String apply(Version json) {
                    return json.downloads.server.url;
                }
            }));


        }

        ObtainFernFlowerTask mcpTask = makeTask("downloadMcpTools", ObtainFernFlowerTask.class);
        {
            mcpTask.setMcpUrl(delayedString(Constants.MCP_URL));
            mcpTask.setFfJar(delayedFile(Constants.FERNFLOWER));
        }

        etagDlTask = makeTask("getAssetsIndex", EtagDownloadTask.class);
        {
            etagDlTask.getInputs().file(delayedFile(Constants.VERSION_JSON));
            etagDlTask.dependsOn("getVersionJson");

            etagDlTask.setUrl(new GetDataFromJson(new VersionToString() {
                @Override
                public String apply(Version json) {
                    return json.assetIndex.url;
                }
            }));


            etagDlTask.setFile(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json"));
            etagDlTask.setDieWithError(false);

            etagDlTask.doLast(new Action<Task>() {
                @Override
                public void execute(Task task1) {
                    try {
                        parseAssetIndex();
                    } catch (JsonSyntaxException e) {
                        throw new RuntimeException(e);
                    } catch (JsonIOException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            etagDlTask.getOutputs().upToDateWhen(new Closure<Boolean>(this, null)  {
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

    public void addMavenRepo(Project proj, final String name, final String url)
    {
        proj.getRepositories().maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository repo)
            {
                repo.setName(name);
                repo.setUrl(url);
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

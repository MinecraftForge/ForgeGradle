package net.minecraftforge.gradle.common;

import static net.minecraftforge.gradle.common.Constants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.FileLogListenner;
import net.minecraftforge.gradle.GradleConfigurationException;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.Version;
import net.minecraftforge.gradle.tasks.DownloadAssetsTask;
import net.minecraftforge.gradle.tasks.ExtractConfigTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ObtainFernFlowerTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.abstractutil.EtagDownloadTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>, IDelayedResolver<K>
{
    public Project    project;
    public BasePlugin<?> otherPlugin;
    public Version    version;
    public AssetIndex assetIndex;

    @SuppressWarnings("rawtypes")
    @Override
    public final void apply(Project arg)
    {
        project = arg;
        
        // check for gradle version
        {
            List<String> split = Splitter.on('.').splitToList(project.getGradle().getGradleVersion());
            int major = Integer.parseInt(split.get(0));
            int minor = Integer.parseInt(split.get(1));
            
            if (major <= 1 || (major == 2 && minor < 3))
                throw new RuntimeException("ForgeGradle 2.0 requires Gradle 2.3 or above.");
        }

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
                    throw new GradleConfigurationException("Seems you are trying to apply 2 ForgeGradle plugins that are not designed to overlay... Fix your buildscripts.");
                }
            }
        }

        // logging
        FileLogListenner listener = new FileLogListenner(project.file(LOG));
        project.getLogging().addStandardOutputListener(listener);
        project.getLogging().addStandardErrorListener(listener);
        project.getGradle().addBuildListener(listener);

        if (project.getBuildDir().getAbsolutePath().contains("!"))
        {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // extension objects
        project.getExtensions().create(EXT_NAME_MC, getExtensionClass(), this);
        project.getExtensions().create(EXT_NAME_JENKINS, JenkinsExtension.class, project);

        // repos
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addMavenRepo(proj, "forge", URL_FORGE_MAVEN);
                proj.getRepositories().mavenCentral();
                addMavenRepo(proj, "minecraft", URL_LIBRARY);
            }
        });

        // do Mcp Snapshots Stuff
        setVersionInfoJson();
        project.getConfigurations().create(CONFIG_MCP_DATA);
        project.getConfigurations().create(CONFIG_MAPPINGS);

        // after eval
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                // dont continue if its already failed!
                if (project.getState().getFailure() != null)
                    return;
                
                afterEvaluate();

                try
                {
                    if (version != null)
                    {
                        File index = delayedFile(ASSETS + "/indexes/" + version.getAssets() + ".json").call();
                        if (index.exists())
                            parseAssetIndex();
                    }
                }
                catch (Exception e)
                {
                    Throwables.propagate(e);
                }
            }
        });

        // some default tasks
        makeCommonTasks();

        // at last, apply the child plugins
        applyPlugin();
    }

    public abstract void applyPlugin();

    public abstract void applyOverlayPlugin();

    /**
     * return true if this plugin can be applied over another BasePlugin.
     * @return TRUE if this can be applied upon another base plugin.
     */
    public abstract boolean canOverlayPlugin();

    protected abstract DelayedFile getDevJson();

    private static boolean displayBanner = true;
    
    private void setVersionInfoJson()
    {
        File jsonCache = cacheFile("McpMappings.json");
        File etagFile = new File(jsonCache.getAbsolutePath() + ".etag");
        
        getExtension().mcpJson = JsonFactory.GSON.fromJson(
                getWithEtag(URL_MCP_JSON, jsonCache, etagFile),
                new TypeToken<Map<String, Map<String, int[]>>>() {}.getType() );
    }

    public void afterEvaluate()
    {
        String mcVersion = delayedString(REPLACE_MC_VERSION).call();
        
        project.getDependencies().add(CONFIG_MAPPINGS, ImmutableMap.of(
                "group", "de.oceanlabs.mcp",
                "name", delayedString("mcp_"+REPLACE_MCP_CHANNEL).call(),
                "version", delayedString(REPLACE_MCP_VERSION+"-"+mcVersion).call(),
                "ext", "zip"
                ));
        
        project.getDependencies().add(CONFIG_MCP_DATA, ImmutableMap.of(
                "group", "de.oceanlabs.mcp",
                "name", "mcp",
                "version", mcVersion,
                "classifier", "srg",
                "ext", "zip"
                ));
        

        if (!displayBanner)
            return;
        
        project.getLogger().lifecycle("****************************");
        project.getLogger().lifecycle(" Powered By MCP:             ");
        project.getLogger().lifecycle(" http://mcp.ocean-labs.de/   ");
        project.getLogger().lifecycle(" Searge, ProfMobius, Fesh0r, ");
        project.getLogger().lifecycle(" R4wk, ZeuX, IngisKahn, bspkrs");
        project.getLogger().lifecycle(" MCP Data version : " + getExtension().getMcpVersion());
        project.getLogger().lifecycle("****************************");
        displayBanner = false;
    }

    @SuppressWarnings("serial")
    private void makeCommonTasks()
    {
        // download tasks
        DownloadTask dlClient = makeTask("downloadClient", DownloadTask.class);
        {
            dlClient.setOutput(delayedFile(JAR_CLIENT_FRESH));
            dlClient.setUrl(delayedString(URL_MC_JAR));
        }

        DownloadTask dlServer = makeTask("downloadServer", DownloadTask.class);
        {
            dlServer.setOutput(delayedFile(JAR_SERVER_FRESH));
            dlServer.setUrl(delayedString(URL_MC_SERVER)); 
        }
        
        EtagDownloadTask etagDlTask = makeTask("getAssetsIndex", EtagDownloadTask.class);
        {
            etagDlTask.setUrl(delayedString(ASSETS_INDEX_URL));
            etagDlTask.setFile(delayedFile(ASSETS + "/indexes/{ASSET_INDEX}.json"));
            etagDlTask.setDieWithError(false);

            etagDlTask.doLast(new Action<Task>() {
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
        }
        
        etagDlTask = makeTask("getVersionJson", EtagDownloadTask.class);
        {
            etagDlTask.setUrl(delayedString(URL_MC_JSON));
            etagDlTask.setFile(delayedFile(JSON_VERSION));
            etagDlTask.setDieWithError(false);
            etagDlTask.doLast(new Closure<Boolean>(project) // normalizes to linux endings
            {
                @Override
                public Boolean call()
                {
                    try
                    {
                        File json = delayedFile(JSON_VERSION).call();
                        if (!json.exists())
                            return true;

                        List<String> lines = Files.readLines(json, Charsets.UTF_8);
                        StringBuilder buf = new StringBuilder();
                        for (String line : lines)
                        {
                            buf = buf.append(line).append('\n');
                        }
                        Files.write(buf.toString().getBytes(Charsets.UTF_8), json);
                    }
                    catch (Throwable t)
                    {
                        Throwables.propagate(t);
                    }
                    return true;
                }
            });
        }
        
        // special DL.. because fernflower.
        ObtainFernFlowerTask ffTask = makeTask("downloadFernFlower", ObtainFernFlowerTask.class);
        {
            ffTask.setMcpUrl(delayedString(URL_FF));
            ffTask.setFfJar(delayedFile(FERNFLOWER));
        }

        DownloadAssetsTask getAssets = makeTask("getAssets", DownloadAssetsTask.class);
        {
            getAssets.setAssetsDir(delayedFile(ASSETS));
            getAssets.setIndex(getAssetIndexClosure());
            getAssets.setIndexName(delayedString("{ASSET_INDEX}"));
            getAssets.dependsOn("getAssetsIndex");
        }

        Delete clearCache = makeTask("cleanCache", Delete.class);
        {
            clearCache.delete(delayedFile(REPLACE_CACHE_DIR));
            clearCache.setGroup("ForgeGradle");
            clearCache.setDescription("Cleares the ForgeGradle cache. DONT RUN THIS unless you want a fresh start, or the dev tells you to.");
        }

        ExtractConfigTask extractMcpMappings = makeTask("extractMcpMappings", ExtractConfigTask.class);
        {
            extractMcpMappings.setOut(delayedFile(DIR_MCP_MAPPINGS));
            extractMcpMappings.setConfig(CONFIG_MAPPINGS);
            extractMcpMappings.setDoesCache(true);
        }
        
        ExtractConfigTask extractMcpData = makeTask("extractMcpData", ExtractConfigTask.class);
        {
            extractMcpData.setOut(delayedFile(DIR_MCP_DATA));
            extractMcpData.setConfig(CONFIG_MCP_DATA);
            extractMcpData.setDoesCache(true);
        }
        
        GenSrgTask genSrgs = makeTask("genSrgs", GenSrgTask.class);
        {
            genSrgs.setInSrg(delayedFile(MCP_DATA_SRG));
            genSrgs.setInExc(delayedFile(MCP_DATA_EXC));
            genSrgs.setMethodsCsv(delayedFile(CSV_METHOD));
            genSrgs.setFieldsCsv(delayedFile(CSV_FIELD));
            genSrgs.setNotchToSrg(delayedFile(Constants.SRG_NOTCH_TO_SRG));
            genSrgs.setNotchToMcp(delayedFile(Constants.SRG_NOTCH_TO_MCP));
            genSrgs.setMcpToSrg(delayedFile(SRG_MCP_TO_SRG));
            genSrgs.setMcpToNotch(delayedFile(SRG_MCP_TO_NOTCH));
            genSrgs.setSrgExc(delayedFile(EXC_SRG));
            genSrgs.setMcpExc(delayedFile(EXC_MCP));
            genSrgs.dependsOn(extractMcpData, extractMcpMappings);
        }
        
        MergeJarsTask merge = makeTask("mergeJars", MergeJarsTask.class);
        {
            merge.setClient(delayedFile(JAR_CLIENT_FRESH));
            merge.setServer(delayedFile(JAR_SERVER_FRESH));
            merge.setOutJar(delayedFile(JAR_MERGED));
            merge.dependsOn(dlClient, dlServer);
        }
    }

    public void parseAssetIndex() throws JsonSyntaxException, JsonIOException, IOException
    {
        assetIndex = JsonFactory.loadAssetsIndex(delayedFile(ASSETS + "/indexes/{ASSET_INDEX}.json").call());
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
     * @return the extension object with name
     * @see Constants#EXT_NAME_MC
     */
    @SuppressWarnings("unchecked")
    public final K getExtension()
    {
        if (otherPlugin != null && canOverlayPlugin())
            return getOverlayExtension();
        else
            return (K) project.getExtensions().getByName(EXT_NAME_MC);
    }

    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants#EXT_NAME_MC
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
        return (T) proj.task(ImmutableMap.of("name", name, "type", type), name);
    }

    public static Project buildProject(File buildFile, Project parent)
    {
        ProjectBuilder builder = ProjectBuilder.builder();
        if (buildFile != null)
        {
            builder = builder.withProjectDir(buildFile.getParentFile()).withName(buildFile.getParentFile().getName());
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
            project.apply(ImmutableMap.of("from", buildFile.getAbsolutePath()));
        }

        return project;
    }

    public void applyExternalPlugin(String plugin)
    {
        project.apply(ImmutableMap.of("plugin", plugin));
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
    
    protected String getWithEtag(String strUrl, File cache, File etagFile)
    {
        try
        {
            if (project.getGradle().getStartParameter().isOffline()) // dont even try the internet
                return Files.toString(cache, Charsets.UTF_8);
            
            // dude, its been less than 1 minute since the last time..
            if (cache.exists() && cache.lastModified() + 60000 >= System.currentTimeMillis())
                return Files.toString(cache, Charsets.UTF_8);

            String etag;
            if (etagFile.exists())
            {
                etag = Files.toString(etagFile, Charsets.UTF_8);
            }
            else
            {
                etagFile.getParentFile().mkdirs();
                etag = "";
            }
            
            URL url = new URL(strUrl);
            
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", USER_AGENT);
            con.setIfModifiedSince(cache.lastModified());
            
            if (!Strings.isNullOrEmpty(etag))
            {
                con.setRequestProperty("If-None-Match", etag);
            }
            
            con.connect();
            
            String out =  null;
            if (con.getResponseCode() == 304)
            {
                // the existing file is good
                Files.touch(cache); // touch it to update last-modified time, to wait another minute
                out =  Files.toString(cache, Charsets.UTF_8);
            }
            else if (con.getResponseCode() == 200)
            {
                InputStream stream = con.getInputStream();
                byte[] data = ByteStreams.toByteArray(stream);
                Files.write(data, cache);
                stream.close();
                
                // write etag
                etag = con.getHeaderField("ETag");
                if (Strings.isNullOrEmpty(etag))
                {
                    Files.touch(etagFile);
                }
                else
                {
                    Files.write(etag, etagFile, Charsets.UTF_8);
                }
                
                out = new String(data);
            }
            else
            {
                project.getLogger().error("Etag download for "+strUrl + " failed with code "+con.getResponseCode());
            }
            
            con.disconnect();
            
            return out;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        
        if (cache.exists())
        {
            try
            {
                return Files.toString(cache, Charsets.UTF_8);
            }
            catch (IOException e)
            {
                Throwables.propagate(e);
            }
        }
        
        throw new RuntimeException("Unable to obtain url ("+strUrl + ") with etag!");
    }

    @Override
    public String resolve(String pattern, Project project, K exten)
    {
        if (version != null)
            pattern = pattern.replace("{ASSET_INDEX}", version.getAssets());

        pattern = pattern.replace("{MCP_DATA_DIR}", DIR_MCP_MAPPINGS);

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
    
    protected File cacheFile(String path)
    {
        return new File(project.getGradle().getGradleUserHomeDir(), "caches/minecraft/" + path);
    }

}

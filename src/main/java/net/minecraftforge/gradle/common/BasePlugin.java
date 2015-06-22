package net.minecraftforge.gradle.common;

import static net.minecraftforge.gradle.common.Constants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.tasks.*;
import net.minecraftforge.gradle.util.FileLogListenner;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.gradle.util.delayed.DelayedFileTree;
import net.minecraftforge.gradle.util.delayed.DelayedString;
import net.minecraftforge.gradle.util.delayed.TokenReplacer;
import net.minecraftforge.gradle.util.json.JsonFactory;
import net.minecraftforge.gradle.util.json.version.Version;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.reflect.TypeToken;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>
{
    public Project       project;
    public BasePlugin<?> otherPlugin;

    @SuppressWarnings({ "rawtypes", "unchecked" })
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

                    // copy the caches before anything uses them
                    otherPlugin.replacerCache = replacerCache;
                    otherPlugin.stringCache = stringCache;
                    otherPlugin.fileCache = fileCache;

                    applyOverlayPlugin();
                    return;
                }
                else
                {
                    throw new GradleConfigurationException("Seems you are trying to apply 2 ForgeGradle plugins that are not designed to overlay... Fix your buildscripts.");
                }
            }
        }

        if (project.getBuildDir().getAbsolutePath().contains("!"))
        {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // set the obvious replacements
        TokenReplacer.putReplacement(REPLACE_CACHE_DIR, cacheFile("").getAbsolutePath());
        TokenReplacer.putReplacement(REPLACE_BUILD_DIR, project.getBuildDir().getAbsolutePath());

        // logging
        {
            File projectCacheDir = project.getGradle().getStartParameter().getProjectCacheDir();
            if (projectCacheDir == null)
                projectCacheDir = new File(project.getProjectDir(), ".gradle");

            TokenReplacer.putReplacement(REPLACE_PROJECT_CACHE_DIR, projectCacheDir.getAbsolutePath());

            FileLogListenner listener = new FileLogListenner(new File(projectCacheDir, "gradle.log"));
            project.getLogging().addStandardOutputListener(listener);
            project.getLogging().addStandardErrorListener(listener);
            project.getGradle().addBuildListener(listener);
        }

        // extension objects
        {
            Type t = getClass().getGenericSuperclass();

            while (t instanceof Class)
            {
                t = ((Class) t).getGenericSuperclass();
            }

            project.getExtensions().create(EXT_NAME_MC, (Class<K>) ((ParameterizedType) t).getActualTypeArguments()[0], this);
        }

        // add buildscript usable tasks
        {
            ExtraPropertiesExtension ext = project.getExtensions().getExtraProperties();
            ext.set("SignJar", SignJar.class);
            ext.set("Download", Download.class);
            ext.set("EtagDownload", EtagDownloadTask.class);
            ext.set("CrowdinDownload", CrowdinDownload.class);
            ext.set("JenkinsChangelog", JenkinsChangelog.class);
        }

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
        project.getConfigurations().maybeCreate(CONFIG_MCP_DATA);
        project.getConfigurations().maybeCreate(CONFIG_MAPPINGS);

        // set other useful configs
        project.getConfigurations().maybeCreate(CONFIG_MC_DEPS);
        project.getConfigurations().maybeCreate(CONFIG_NATIVES);

        // after eval
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                // dont continue if its already failed!
                if (project.getState().getFailure() != null)
                    return;

                afterEvaluate();
            }
        });

        // some default tasks
        makeCommonTasks();

        // at last, apply the child plugins
        applyPlugin();
    }

    public abstract void applyPlugin();

    protected abstract void applyOverlayPlugin();

    /**
     * return true if this plugin can be applied over another BasePlugin.
     * @return TRUE if this can be applied upon another base plugin.
     */
    public abstract boolean canOverlayPlugin();

    private static boolean displayBanner = true;

    private void setVersionInfoJson()
    {
        File jsonCache = cacheFile("McpMappings.json");
        File etagFile = new File(jsonCache.getAbsolutePath() + ".etag");

        getExtension().mcpJson = JsonFactory.GSON.fromJson(getWithEtag(URL_MCP_JSON, jsonCache, etagFile), new TypeToken<Map<String, Map<String, int[]>>>() {}.getType());
    }

    protected void afterEvaluate()
    {
        // validate MC version
        if (Strings.isNullOrEmpty(getExtension().getVersion()))
        {
            throw new GradleConfigurationException("You must set the Minecraft version!");
        }

        // http://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp/1.7.10/mcp-1.7.10-srg.zip
        project.getDependencies().add(CONFIG_MAPPINGS, ImmutableMap.of(
                "group", "de.oceanlabs.mcp",
                "name", delayedString("mcp_" + REPLACE_MCP_CHANNEL).call(),
                "version", delayedString(REPLACE_MCP_VERSION + "-" + REPLACE_MC_VERSION).call(),
                "ext", "zip"
                ));

        project.getDependencies().add(CONFIG_MCP_DATA, ImmutableMap.of(
                "group", "de.oceanlabs.mcp",
                "name", "mcp",
                "version", delayedString(REPLACE_MC_VERSION).call(),
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
        Download dlClient = makeTask(TASK_DL_CLIENT, Download.class);
        {
            dlClient.setOutput(delayedFile(JAR_CLIENT_FRESH));
            dlClient.setUrl(delayedString(URL_MC_CLIENT));
        }

        Download dlServer = makeTask(TASK_DL_SERVER, Download.class);
        {
            dlServer.setOutput(delayedFile(JAR_SERVER_FRESH));
            dlServer.setUrl(delayedString(URL_MC_SERVER));
        }

        MergeJars merge = makeTask(TASK_MERGE_JARS, MergeJars.class);
        {
            merge.setClient(delayedFile(JAR_CLIENT_FRESH));
            merge.setServer(delayedFile(JAR_SERVER_FRESH));
            merge.setOutJar(delayedFile(JAR_MERGED));
            merge.dontProcess("org/bouncycastle");
            merge.dontProcess("org/apache");
            merge.dontProcess("com/google");
            merge.dontProcess("com/mojang/authlib");
            merge.dontProcess("com/mojang/util");
            merge.dontProcess("gnu/trove");
            merge.dontProcess("io/netty");
            merge.dontProcess("javax/annotation");
            merge.dontProcess("argo");
            merge.dependsOn(dlClient, dlServer);

            merge.setGroup(null);
            merge.setDescription(null);
        }

        EtagDownloadTask getVersionJson = makeTask(TASK_DL_VERSION_JSON, EtagDownloadTask.class);
        {
            getVersionJson.setUrl(delayedString(URL_MC_JSON));
            getVersionJson.setFile(delayedFile(JSON_VERSION));
            getVersionJson.setDieWithError(false);
            getVersionJson.doLast(new Closure<Boolean>(project) // normalizes to linux endings
            {
                @Override
                public Boolean call()
                {
                    try
                    {
                        // normalize the line endings...
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

                        // grab the AssetIndex if it isnt already there
                        if (!TokenReplacer.hasReplacement(REPLACE_ASSET_INDEX))
                        {
                            parseAndStoreVersion(json, json.getParentFile());
                        }
                    }
                    catch (Throwable t)
                    {
                        Throwables.propagate(t);
                    }
                    return true;
                }
            });
        }

        ExtractConfigTask extractNatives = makeTask(TASK_EXTRACT_NATIVES, ExtractConfigTask.class);
        {
            extractNatives.setDestinationDir(delayedFile(DIR_NATIVES));
            extractNatives.setConfig(CONFIG_NATIVES);
            extractNatives.exclude("META-INF/**", "META-INF/**");
            extractNatives.setDoesCache(true);
            extractNatives.dependsOn(getVersionJson);
        }

        EtagDownloadTask getAssetsIndex = makeTask(TASK_DL_ASSET_INDEX, EtagDownloadTask.class);
        {
            getAssetsIndex.setUrl(delayedString(ASSETS_INDEX_URL));
            getAssetsIndex.setFile(delayedFile(JSON_ASSET_INDEX));
            getAssetsIndex.setDieWithError(false);
            getAssetsIndex.dependsOn(getVersionJson);
        }

        DownloadAssetsTask getAssets = makeTask(TASK_DL_ASSETS, DownloadAssetsTask.class);
        {
            getAssets.setAssetsDir(delayedFile(DIR_ASSETS));
            getAssets.setAssetsIndex(delayedFile(JSON_ASSET_INDEX));
            getAssets.dependsOn(getAssetsIndex);
        }

        ExtractConfigTask extractMcpData = makeTask(TASK_EXTRACT_MCP, ExtractConfigTask.class);
        {
            extractMcpData.setDestinationDir(delayedFile(DIR_MCP_DATA));
            extractMcpData.setConfig(CONFIG_MCP_DATA);
            extractMcpData.setDoesCache(true);
        }

        ExtractConfigTask extractMcpMappings = makeTask(TASK_EXTRACT_MAPPINGS, ExtractConfigTask.class);
        {
            extractMcpMappings.setDestinationDir(delayedFile(DIR_MCP_MAPPINGS));
            extractMcpMappings.setConfig(CONFIG_MAPPINGS);
            extractMcpMappings.setDoesCache(true);
        }

        GenSrgs genSrgs = makeTask(TASK_GENERATE_SRGS, GenSrgs.class);
        {
            genSrgs.setInSrg(delayedFile(MCP_DATA_SRG));
            genSrgs.setInExc(delayedFile(MCP_DATA_EXC));
            genSrgs.setMethodsCsv(delayedFile(CSV_METHOD));
            genSrgs.setFieldsCsv(delayedFile(CSV_FIELD));
            genSrgs.setNotchToSrg(delayedFile(Constants.SRG_NOTCH_TO_SRG));
            genSrgs.setNotchToMcp(delayedFile(Constants.SRG_NOTCH_TO_MCP));
            genSrgs.setSrgToMcp(delayedFile(SRG_SRG_TO_MCP));
            genSrgs.setMcpToSrg(delayedFile(SRG_MCP_TO_SRG));
            genSrgs.setMcpToNotch(delayedFile(SRG_MCP_TO_NOTCH));
            genSrgs.setSrgExc(delayedFile(EXC_SRG));
            genSrgs.setMcpExc(delayedFile(EXC_MCP));
            genSrgs.setDoesCache(true);
            genSrgs.dependsOn(extractMcpData, extractMcpMappings);
        }

        ObtainFernFlowerTask ffTask = makeTask(TASK_DL_FERNFLOWER, ObtainFernFlowerTask.class);
        {
            ffTask.setMcpUrl(delayedString(URL_FF));
            ffTask.setFfJar(delayedFile(JAR_FERNFLOWER));
            ffTask.setDoesCache(true);
        }

        Delete clearCache = makeTask(TASK_CLEAN_CACHE, Delete.class);
        {
            clearCache.delete(delayedFile(REPLACE_CACHE_DIR), delayedFile(REPLACE_PROJECT_CACHE_DIR));
            clearCache.setGroup(GROUP_FG);
            clearCache.setDescription("Cleares the ForgeGradle cache. DONT RUN THIS unless you want a fresh start, or the dev tells you to.");
        }
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

    public DefaultTask maybeMakeTask(String name)
    {
        return maybeMakeTask(name, DefaultTask.class);
    }

    public <T extends Task> T makeTask(String name, Class<T> type)
    {
        return makeTask(project, name, type);
    }

    public <T extends Task> T maybeMakeTask(String name, Class<T> type)
    {
        return maybeMakeTask(project, name, type);
    }

    public static <T extends Task> T maybeMakeTask(Project proj, String name, Class<T> type)
    {
        return (T) proj.getTasks().maybeCreate(name, type);
    }

    public static <T extends Task> T makeTask(Project proj, String name, Class<T> type)
    {
        return (T) proj.getTasks().create(name, type);
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

            String out = null;
            if (con.getResponseCode() == 304)
            {
                // the existing file is good
                Files.touch(cache); // touch it to update last-modified time, to wait another minute
                out = Files.toString(cache, Charsets.UTF_8);
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
                project.getLogger().error("Etag download for " + strUrl + " failed with code " + con.getResponseCode());
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

        throw new RuntimeException("Unable to obtain url (" + strUrl + ") with etag!");
    }

    /**
     * Parses the version json in the provided file, and saves it in memory.
     * Also populates the McDeps and natives configurations.
     * Also sets the ASSET_INDEX replacement string
     * Does nothing (returns null) if the file is not found, but hard-crashes if it could not be parsed.
     * @param file version file to parse
     * @param inheritanceDirs folders to look for the parent json
     * @return NULL if the file doesnt exist
     */
    protected Version parseAndStoreVersion(File file, File... inheritanceDirs)
    {
        if (!file.exists())
            return null;

        Version version = null;

        try
        {
            version = JsonFactory.loadVersion(file, delayedFile(Constants.DIR_JSONS).call());
        }
        catch (Exception e)
        {
            project.getLogger().error("" + file + " could not be parsed");
            Throwables.propagate(e);
        }

        if (version == null)
        {
            try
            {
                version = JsonFactory.loadVersion(file, delayedFile(Constants.DIR_JSONS).call());
            }
            catch (Exception e)
            {
                project.getLogger().error("" + file + " could not be parsed");
                Throwables.propagate(e);
            }
        }

        // apply the dep info.
        DependencyHandler handler = project.getDependencies();

        // actual dependencies
        if (project.getConfigurations().getByName(CONFIG_MC_DEPS).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.util.json.version.Library lib : version.getLibraries())
            {
                if (lib.natives == null)
                    handler.add(CONFIG_MC_DEPS, lib.getArtifactName());
            }
        }
        else
            project.getLogger().debug("RESOLVED: " + CONFIG_MC_DEPS);

        // the natives
        if (project.getConfigurations().getByName(CONFIG_NATIVES).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.util.json.version.Library lib : version.getLibraries())
            {
                if (lib.natives != null)
                    handler.add(CONFIG_NATIVES, lib.getArtifactName());
            }
        }
        else
            project.getLogger().debug("RESOLVED: " + CONFIG_NATIVES);

        // set asset index
        TokenReplacer.putReplacement(REPLACE_ASSET_INDEX, version.getAssets());

        return version;
    }

    protected Version parseAndStoreVersion(File file)
    {
        return parseAndStoreVersion(file, delayedFile(DIR_JSONS).call());
    }

    // DELAYED STUFF ONLY ------------------------------------------------------------------------
    private LoadingCache<String, TokenReplacer> replacerCache = CacheBuilder.newBuilder()
            .weakValues()
            .build(
                    new CacheLoader<String, TokenReplacer>() {
                        public TokenReplacer load(String key)
                        {
                            return new TokenReplacer(key);
                        }
                    });
    private LoadingCache<String, DelayedString> stringCache = CacheBuilder.newBuilder()
            .weakValues()
            .build(
                    new CacheLoader<String, DelayedString>() {
                        public DelayedString load(String key)
                        {
                            return new DelayedString(replacerCache.getUnchecked(key));
                        }
                    });
    private LoadingCache<String, DelayedFile> fileCache = CacheBuilder.newBuilder()
            .weakValues()
            .build(
                    new CacheLoader<String, DelayedFile>() {
                        public DelayedFile load(String key)
                        {
                            return new DelayedFile(project, replacerCache.getUnchecked(key));
                        }
                    });

    protected DelayedString delayedString(String path)
    {
        return stringCache.getUnchecked(path);
    }

    protected DelayedFile delayedFile(String path)
    {
        return fileCache.getUnchecked(path);
    }

    protected DelayedFileTree delayedTree(String path)
    {
        return new DelayedFileTree(project, replacerCache.getUnchecked(path));
    }

    protected File cacheFile(String path)
    {
        return new File(project.getGradle().getGradleUserHomeDir(), "caches/minecraft/" + path);
    }
}

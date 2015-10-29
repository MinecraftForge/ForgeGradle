package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.ExtractConfigTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.SourceCopyTask;
import net.minecraftforge.gradle.tasks.user.reobf.ArtifactSpec;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;

public abstract class UserBasePlugin<T extends UserExtension> extends BasePlugin<T>
{
    @SuppressWarnings("serial")
    @Override
    public void applyPlugin()
    {
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("maven");
        this.applyExternalPlugin("eclipse");
        this.applyExternalPlugin("idea");

        hasScalaBefore = project.getPlugins().hasPlugin("scala");
        hasGroovyBefore = project.getPlugins().hasPlugin("groovy");

        addGitIgnore(); //Morons -.-

        configureDeps();
        configureCompilation();
        configureIntellij();

        // create basic tasks.
        tasks();

        // create lifecycle tasks.

        Task task = makeTask("setupCIWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar");
        task.setDescription("Sets up the bare minimum to build a minecraft mod. Idea for CI servers");
        task.setGroup("ForgeGradle");
        //configureCISetup(task);

        task = makeTask("setupDevWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar", "makeStart");
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");
        //configureDevSetup(task);

        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "makeStart", "repackMinecraft");
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");
        //configureDecompSetup(task);

        project.getGradle().getTaskGraph().whenReady(new Closure<Object>(this, null) {
            @Override
            public Object call()
            {
                TaskExecutionGraph graph = project.getGradle().getTaskGraph();
                String path = project.getPath();
                
                graph.getAllTasks().clear();

                if (graph.hasTask(path + "setupDecompWorkspace"))
                {
                    getExtension().setDecomp();
                    configurePostDecomp(true, true);
                }
                return null;
            }

            @Override
            public Object call(Object obj)
            {
                return call();
            }

            @Override
            public Object call(Object... obj)
            {
                return call();
            }
        });
    }

    private boolean hasAppliedJson = false;
    private boolean hasScalaBefore = false;
    private boolean hasGroovyBefore = false;

    /**
     * may not include delayed tokens.
     * @return name of API, eg "forge", "fml", "liteloader"
     */
    public abstract String getApiName();
    /**
     * Name of the source dependency.  eg: forgeSrc
     * may not include delayed tokens.
     * @return the name of the recompiled dependency
     */
    protected abstract String getSrcDepName();
    /**
     * Name of the source dependency.  eg: forgeBin
     * may not include delayed tokens.
     * @return the name of the bin-patched dependency
     */
    protected abstract String getBinDepName();

    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     * @return has an api version
     */
    protected abstract boolean hasApiVersion();
    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     * @param exten the extension object
     * @return the api version
     */
    protected abstract String getApiVersion(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     * @param exten the extension object
     * @return the MC version
     */
    protected abstract String getMcVersion(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * This unlike the others, is evaluated as a delayed file, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     * @param exten the extension object
     * @return the API cache dir
     */
    protected abstract String getApiCacheDir(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * This unlike the others, is evaluated as a delayed file, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     * @param exten the extension object
     * @return the SRG cache dir
     */
    protected abstract String getSrgCacheDir(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * This unlike the others, is evaluated as a delayed file, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     * @param exten the extension object
     * @return the userdev cache dir
     */
    protected abstract String getUserDevCacheDir(T exten);
    /**
     * This unlike the others, is evaluated as a delayed string, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     * @return the userdev dep string
     */
    protected abstract String getUserDev();

    /**
     * For run configurations. Is delayed.
     * @return the client tweaker class name
     */
    protected abstract String getClientTweaker();
    /**
     * For run configurations. Is delayed.
     * @return the server tweaker class name
     */
    protected abstract String getServerTweaker();
    /**
     * For run configurations
     * @return the start location
     */
    protected abstract String getStartDir();
    /**
     * For run configurations. Is delayed.
     * @return the client main class name
     */
    protected abstract String getClientRunClass();
    /**
     * For run configurations
     * @return the client run arguments
     */
    protected abstract Iterable<String> getClientRunArgs();
    /**
     * For run configurations. Is delayed.
     * @return the server main class name
     */
    protected abstract String getServerRunClass();
    /**
     * For run configurations
     * @return the server run arguments
     */
    protected abstract Iterable<String> getServerRunArgs();

//    protected abstract void configureCISetup(Task task);
//    protected abstract void configureDevSetup(Task task);
//    protected abstract void configureDecompSetup(Task task);

    @Override
    public String resolve(String pattern, Project project, T exten)
    {
        pattern = super.resolve(pattern, project, exten);

        pattern = pattern.replace("{MCP_DATA_DIR}", CONF_DIR);
        pattern = pattern.replace("{USER_DEV}", this.getUserDevCacheDir(exten));
        pattern = pattern.replace("{SRG_DIR}", this.getSrgCacheDir(exten));
        pattern = pattern.replace("{API_CACHE_DIR}", this.getApiCacheDir(exten));
        pattern = pattern.replace("{MC_VERSION}", getMcVersion(exten));

        // do run config stuff.
        pattern = pattern.replace("{RUN_CLIENT_TWEAKER}", getClientTweaker());
        pattern = pattern.replace("{RUN_SERVER_TWEAKER}", getServerTweaker());
        pattern = pattern.replace("{RUN_BOUNCE_CLIENT}", getClientRunClass());
        pattern = pattern.replace("{RUN_BOUNCE_SERVER}", getServerRunClass());

        if (!exten.mappingsSet())
        {
            // no mappings set?remove these tokens
            pattern = pattern.replace("{MAPPING_CHANNEL}", "");
            pattern = pattern.replace("{MAPPING_VERSION}", "");
        }

        if (hasApiVersion())
            pattern = pattern.replace("{API_VERSION}", getApiVersion(exten));

        pattern = pattern.replace("{API_NAME}", getApiName());
        return pattern;
    }

    protected void configureDeps()
    {
        // create configs
        project.getConfigurations().create(CONFIG_USERDEV);
        project.getConfigurations().create(CONFIG_NATIVES);
        project.getConfigurations().create(CONFIG_START);
        project.getConfigurations().create(CONFIG_DEPS);
        project.getConfigurations().create(CONFIG_MC);

        // special userDev stuff
        ExtractConfigTask extractUserDev = makeTask("extractUserDev", ExtractConfigTask.class);
        extractUserDev.setOut(delayedFile("{USER_DEV}"));
        extractUserDev.setConfig(CONFIG_USERDEV);
        extractUserDev.setDoesCache(true);
        extractUserDev.dependsOn("getVersionJson");
        extractUserDev.doLast(new Action<Task>()
        {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(getDevJson().call(), CONFIG_DEPS, CONFIG_NATIVES, arg0.getLogger());
            }
        });
        project.getTasks().findByName("getAssetsIndex").dependsOn("extractUserDev");

        // special native stuff
        ExtractConfigTask extractNatives = makeTask("extractNatives", ExtractConfigTask.class);
        extractNatives.setOut(delayedFile(Constants.NATIVES_DIR));
        extractNatives.setConfig(CONFIG_NATIVES);
        extractNatives.exclude("META-INF/**", "META-INF/**");
        extractNatives.doesCache();
        extractNatives.dependsOn("extractUserDev");

        // special gradleStart stuff
        project.getDependencies().add(CONFIG_START, project.files(delayedFile(getStartDir())));

        // extra libs folder.
        project.getDependencies().add("compile", project.fileTree("libs"));

        // make MC dependencies into normal compile classpath
        project.getDependencies().add("compile", project.getConfigurations().getByName(CONFIG_DEPS));
        project.getDependencies().add("compile", project.getConfigurations().getByName(CONFIG_MC));
        project.getDependencies().add("runtime", project.getConfigurations().getByName(CONFIG_START));
    }

    /**
     * This mod adds the API sourceSet, and correctly configures the
     */
    protected void configureCompilation()
    {
        // get conventions
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = javaConv.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        SourceSet api = javaConv.getSourceSets().create("api");

        // set the Source
        javaConv.setSourceCompatibility("1.6");
        javaConv.setTargetCompatibility("1.6");

        main.setCompileClasspath(main.getCompileClasspath().plus(api.getOutput()));
        test.setCompileClasspath(test.getCompileClasspath().plus(api.getOutput()));

        project.getConfigurations().getByName("apiCompile").extendsFrom(project.getConfigurations().getByName("compile"));
        project.getConfigurations().getByName("testCompile").extendsFrom(project.getConfigurations().getByName("apiCompile"));
        
        // set compile not to take from libs
        JavaCompile compileTask = ((JavaCompile)project.getTasks().getByName(main.getCompileJavaTaskName()));
        List<String> args = compileTask.getOptions().getCompilerArgs();
        if (args == null || args.isEmpty())
        {
            args = Lists.newArrayList();
        }
        args.add("-sourcepath");
        args.add(".");
        compileTask.getOptions().setCompilerArgs(args);
    }

    private void readAndApplyJson(File file, String depConfig, String nativeConfig, Logger log)
    {
        if (version == null)
        {
            try
            {
                version = JsonFactory.loadVersion(file, delayedFile(Constants.JSONS_DIR).call());
            }
            catch (Exception e)
            {
                log.error("" + file + " could not be parsed");
                Throwables.propagate(e);
            }
        }

        if (hasAppliedJson)
            return;

        // apply the dep info.
        DependencyHandler handler = project.getDependencies();

        // actual dependencies
        if (project.getConfigurations().getByName(depConfig).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.json.version.Library lib : version.getLibraries())
            {
                if (lib.natives == null)
                    handler.add(depConfig, lib.getArtifactName());
            }
        }
        else
            log.debug("RESOLVED: " + depConfig);

        // the natives
        if (project.getConfigurations().getByName(nativeConfig).getState() == State.UNRESOLVED)
        {
            for (net.minecraftforge.gradle.json.version.Library lib : version.getLibraries())
            {
                if (lib.natives != null)
                    handler.add(nativeConfig, lib.getArtifactName());
            }
        }
        else
            log.debug("RESOLVED: " + nativeConfig);

        hasAppliedJson = true;
    }

    @SuppressWarnings("serial")
    protected void configureIntellij()
    {
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        ideaConv.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea").getFiles());
        ideaConv.getModule().setDownloadJavadoc(true);
        ideaConv.getModule().setDownloadSources(true);

        // fix the idea bug
        ideaConv.getModule().setInheritOutputDirs(true);

        Task task = makeTask("genIntellijRuns", DefaultTask.class);
        task.doLast(new Action<Task>() {
            @Override
            public void execute(Task task)
            {
                try
                {
                    String module = task.getProject().getProjectDir().getCanonicalPath();
                    
                    File root = task.getProject().getProjectDir().getCanonicalFile();
                    File file = null;
                    while (file == null && !root.equals(task.getProject().getRootProject().getProjectDir().getCanonicalFile().getParentFile()))
                    {
                        file = new File(root, ".idea/workspace.xml");
                        if (!file.exists())
                        {
                            file = null;
                            // find iws file
                            for (File f : root.listFiles())
                            {
                                if (f.isFile() && f.getName().endsWith(".iws"))
                                {
                                    file = f;
                                    break;
                                }
                            }
                        }
                        
                        root = root.getParentFile();
                    }
                    
                    if (file == null || !file.exists())
                        throw new RuntimeException("Intellij workspace file could not be found! are you sure you imported the project into intellij?");

                    DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
                    Document doc = docBuilder.parse(file);

                    injectIntellijRuns(doc, module);

                    // write the content into xml file
                    TransformerFactory transformerFactory = TransformerFactory.newInstance();
                    Transformer transformer = transformerFactory.newTransformer();
                    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                    transformer.setOutputProperty(OutputKeys.METHOD, "xml");
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

                    DOMSource source = new DOMSource(doc);
                    StreamResult result = new StreamResult(file);
                    //StreamResult result = new StreamResult(System.out);

                    transformer.transform(source, result);
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });

        if (ideaConv.getWorkspace().getIws() == null)
            return;

        ideaConv.getWorkspace().getIws().withXml(new Closure<Object>(this, null)
        {
            public Object call(Object... obj)
            {
                Element root = ((XmlProvider) this.getDelegate()).asElement();
                Document doc = root.getOwnerDocument();
                try
                {
                    injectIntellijRuns(doc, project.getProjectDir().getCanonicalPath());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                return null;
            }
        });
    }

    public final void injectIntellijRuns(Document doc, String module) throws DOMException, IOException
    {
        Element root = null;

        {
            NodeList list = doc.getElementsByTagName("component");
            for (int i = 0; i < list.getLength(); i++)
            {
                Element e = (Element) list.item(i);
                if ("RunManager".equals(e.getAttribute("name")))
                {
                    root = e;
                    break;
                }
            }
        }

        String[][] config = new String[][]
        {
                new String[]
                {
                        "Minecraft Client",
                        GRADLE_START_CLIENT,
                        "-Xincgc -Xmx1024M -Xms1024M",
                        Joiner.on(' ').join(getClientRunArgs())
                },
                new String[]
                {
                        "Minecraft Server",
                        GRADLE_START_SERVER,
                        "-Xincgc -Dfml.ignoreInvalidMinecraftCertificates=true",
                        Joiner.on(' ').join(getServerRunArgs())
                }
        };

        for (String[] data : config)
        {
            Element child = add(root, "configuration",
                    "default", "false",
                    "name", data[0],
                    "type", "Application",
                    "factoryName", "Application",
                    "default", "false");

            add(child, "extension",
                    "name", "coverage",
                    "enabled", "false",
                    "sample_coverage", "true",
                    "runner", "idea");
            add(child, "option", "name", "MAIN_CLASS_NAME", "value", data[1]);
            add(child, "option", "name", "VM_PARAMETERS", "value", data[2]);
            add(child, "option", "name", "PROGRAM_PARAMETERS", "value", data[3]);
            add(child, "option", "name", "WORKING_DIRECTORY", "value", "file://" + delayedFile("{RUN_DIR}").call().getCanonicalPath().replace(module, "$PROJECT_DIR$"));
            add(child, "option", "name", "ALTERNATIVE_JRE_PATH_ENABLED", "value", "false");
            add(child, "option", "name", "ALTERNATIVE_JRE_PATH", "value", "");
            add(child, "option", "name", "ENABLE_SWING_INSPECTOR", "value", "false");
            add(child, "option", "name", "ENV_VARIABLES");
            add(child, "option", "name", "PASS_PARENT_ENVS", "value", "true");
            add(child, "module", "name", ((IdeaModel) project.getExtensions().getByName("idea")).getModule().getName());
            add(child, "envs");
            add(child, "RunnerSettings", "RunnerId", "Run");
            add(child, "ConfigurationWrapper", "RunnerId", "Run");
            add(child, "method");
        }
        File f = delayedFile("{RUN_DIR}").call();
        if (!f.exists())
            f.mkdirs();
    }

    private Element add(Element parent, String name, String... values)
    {
        Element e = parent.getOwnerDocument().createElement(name);
        for (int x = 0; x < values.length; x += 2)
        {
            e.setAttribute(values[x], values[x + 1]);
        }
        parent.appendChild(e);
        return e;
    }

    private void tasks()
    {
        {
            GenSrgTask task = makeTask("genSrgs", GenSrgTask.class);
            task.setInSrg(delayedFile(PACKAGED_SRG));
            task.setInExc(delayedFile(PACKAGED_EXC));
            task.setMethodsCsv(delayedFile(METHOD_CSV));
            task.setFieldsCsv(delayedFile(FIELD_CSV));
            task.setNotchToSrg(delayedFile(DEOBF_SRG_SRG));
            task.setNotchToMcp(delayedFile(DEOBF_MCP_SRG));
            task.setSrgToMcp(delayedFile(DEOBF_SRG_MCP_SRG));
            task.setMcpToSrg(delayedFile(REOBF_SRG));
            task.setMcpToNotch(delayedFile(REOBF_NOTCH_SRG));
            task.setSrgExc(delayedFile(EXC_SRG));
            task.setMcpExc(delayedFile(EXC_MCP));
            task.dependsOn("extractUserDev", "extractMcpData");
        }

        {
            MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
            task.setClient(delayedFile(JAR_CLIENT_FRESH));
            task.setServer(delayedFile(JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(JAR_MERGED));
            task.setMergeCfg(delayedFile(MERGE_CFG));
            task.setMcVersion(delayedString("{MC_VERSION}"));
            task.dependsOn("extractUserDev", "downloadClient", "downloadServer");
        }

        {
            String name = getBinDepName() + "-" + (hasApiVersion() ? "{API_VERSION}" : "{MC_VERSION}") + ".jar";

            ProcessJarTask task = makeTask("deobfBinJar", ProcessJarTask.class);
            task.setSrg(delayedFile(DEOBF_MCP_SRG));
            task.setExceptorJson(delayedFile(EXC_JSON));
            task.setExceptorCfg(delayedFile(EXC_MCP));
            task.setFieldCsv(delayedFile(FIELD_CSV));
            task.setMethodCsv(delayedFile(METHOD_CSV));
            task.setInJar(delayedFile(JAR_MERGED));
            task.setOutCleanJar(delayedFile("{API_CACHE_DIR}/" + MAPPING_APPENDAGE + name));
            task.setOutDirtyJar(delayedFile(DIRTY_DIR + "/" + name));
            task.setApplyMarkers(false);
            task.setStripSynthetics(true);
            configureDeobfuscation(task);
            task.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }

        {
            String name = "{API_NAME}-" + (hasApiVersion() ? "{API_VERSION}" : "{MC_VERSION}") + "-"+ CLASSIFIER_DEOBF_SRG +".jar";

            ProcessJarTask task = makeTask("deobfuscateJar", ProcessJarTask.class);
            task.setSrg(delayedFile(DEOBF_SRG_SRG));
            task.setExceptorJson(delayedFile(EXC_JSON));
            task.setExceptorCfg(delayedFile(EXC_SRG));
            task.setInJar(delayedFile(JAR_MERGED));
            task.setOutCleanJar(delayedFile("{API_CACHE_DIR}/" + name));
            task.setOutDirtyJar(delayedFile(DIRTY_DIR + "/" + name));
            task.setApplyMarkers(true);
            configureDeobfuscation(task);
            task.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }

        {
            ReobfTask task = makeTask("reobf", ReobfTask.class);
            task.dependsOn("genSrgs");
            task.setExceptorCfg(delayedFile(EXC_SRG));
            task.setSrg(delayedFile(REOBF_SRG));
            task.setFieldCsv(delayedFile(FIELD_CSV));
            task.setFieldCsv(delayedFile(METHOD_CSV));
            task.setMcVersion(delayedString("{MC_VERSION}"));

            task.mustRunAfter("test");
            project.getTasks().getByName("assemble").dependsOn(task);
            project.getTasks().getByName("uploadArchives").dependsOn(task);
        }

        {
            // create GradleStart
            CreateStartTask task = makeTask("makeStart", CreateStartTask.class);
            task.addResource("GradleStart.java");
            task.addResource("GradleStartServer.java");
            task.addResource("net/minecraftforge/gradle/GradleStartCommon.java");
            task.addResource("net/minecraftforge/gradle/OldPropertyMapSerializer.java");
            task.addResource("net/minecraftforge/gradle/tweakers/CoremodTweaker.java");
            task.addResource("net/minecraftforge/gradle/tweakers/AccessTransformerTweaker.java");
            task.addReplacement("@@MCVERSION@@", delayedString("{MC_VERSION}"));
            task.addReplacement("@@ASSETINDEX@@", delayedString("{ASSET_INDEX}"));
            task.addReplacement("@@ASSETSDIR@@", delayedFile("{CACHE_DIR}/minecraft/assets"));
            task.addReplacement("@@NATIVESDIR@@", delayedFile(Constants.NATIVES_DIR));
            task.addReplacement("@@SRGDIR@@", delayedFile("{SRG_DIR}"));
            task.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(UserConstants.DEOBF_SRG_SRG));
            task.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(UserConstants.DEOBF_MCP_SRG));
            task.addReplacement("@@SRG_SRG_MCP@@", delayedFile(UserConstants.DEOBF_SRG_MCP_SRG));
            task.addReplacement("@@SRG_MCP_SRG@@", delayedFile(UserConstants.REOBF_SRG));
            task.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(UserConstants.REOBF_NOTCH_SRG));
            task.addReplacement("@@CSVDIR@@", delayedFile("{MCP_DATA_DIR}"));
            task.addReplacement("@@CLIENTTWEAKER@@", delayedString("{RUN_CLIENT_TWEAKER}"));
            task.addReplacement("@@SERVERTWEAKER@@", delayedString("{RUN_SERVER_TWEAKER}"));
            task.addReplacement("@@BOUNCERCLIENT@@", delayedString("{RUN_BOUNCE_CLIENT}"));
            task.addReplacement("@@BOUNCERSERVER@@", delayedString("{RUN_BOUNCE_SERVER}"));
            task.setStartOut(delayedFile(getStartDir()));
            task.compileResources(CONFIG_DEPS);
            
            // see delayed task config for some more config
            
            task.dependsOn("extractUserDev", "getAssets", "getAssetsIndex", "extractNatives");
        }

        createPostDecompTasks();
        createExecTasks();
        createSourceCopyTasks();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final void createPostDecompTasks()
    {
        DelayedFile decompOut = delayedDirtyFile(null, CLASSIFIER_DECOMPILED, "jar", false);
        DelayedFile remapped = delayedDirtyFile(getSrcDepName(), CLASSIFIER_SOURCES, "jar");
        final DelayedFile recomp = delayedDirtyFile(getSrcDepName(), null, "jar");
        final DelayedFile recompSrc = delayedFile(RECOMP_SRC_DIR);
        final DelayedFile recompCls = delayedFile(RECOMP_CLS_DIR);

        DecompileTask decomp = makeTask("decompile", DecompileTask.class);
        {
            decomp.setInJar(delayedDirtyFile(null, CLASSIFIER_DEOBF_SRG, "jar", false));
            decomp.setOutJar(decompOut);
            decomp.setFernFlower(delayedFile(FERNFLOWER));
            decomp.setPatch(delayedFile(MCP_PATCH_DIR));
            decomp.setAstyleConfig(delayedFile(ASTYLE_CFG));
            decomp.dependsOn("downloadMcpTools", "deobfuscateJar", "genSrgs");
        }

        // Remap to MCP names
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.setInJar(decompOut);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV));
            remap.setMethodsCsv(delayedFile(METHOD_CSV));
            remap.setParamsCsv(delayedFile(PARAM_CSV));
            remap.setDoesJavadocs(true);
            remap.dependsOn(decomp);
        }

        Spec onlyIfCheck = new Spec() {
            @Override
            public boolean isSatisfiedBy(Object obj)
            {
                boolean didWork = ((Task) obj).dependsOnTaskDidWork();
                boolean exists = recomp.call().exists();
                if (!exists)
                    return true;
                else
                    return didWork;
            }
        };

        ExtractTask extract = makeTask("extractMinecraftSrc", ExtractTask.class);
        {
            extract.from(remapped);
            extract.into(recompSrc);
            extract.setIncludeEmptyDirs(false);
            extract.setClean(true);
            extract.dependsOn(remap);

            extract.onlyIf(onlyIfCheck);
        }

        JavaCompile recompTask = makeTask("recompMinecraft", JavaCompile.class);
        {
            recompTask.setSource(recompSrc);
            recompTask.setSourceCompatibility("1.6");
            recompTask.setTargetCompatibility("1.6");
            recompTask.setClasspath(project.getConfigurations().getByName(CONFIG_DEPS));
            recompTask.dependsOn(extract);
            recompTask.getOptions().setWarnings(false);

            recompTask.onlyIf(onlyIfCheck);
        }

        Jar repackageTask = makeTask("repackMinecraft", Jar.class);
        {
            repackageTask.from(recompSrc);
            repackageTask.from(recompCls);
            repackageTask.exclude("*.java", "**/*.java", "**.java");
            repackageTask.dependsOn(recompTask);

            // file output configuration done in the delayed configuration.

            repackageTask.onlyIf(onlyIfCheck);
        }
    }

    @SuppressWarnings("serial")
    private class MakeDirExist extends Closure<Boolean>
    {
        DelayedFile path;
        MakeDirExist(DelayedFile path)
        {
            super(project);
            this.path = path;
        }
        @Override
        public Boolean call()
        {
            File f = path.call();
            if (!f.exists())
                f.mkdirs();
            return true;
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void createExecTasks()
    {
        JavaExec exec = makeTask("runClient", JavaExec.class);
        {
            exec.doFirst(new MakeDirExist(delayedFile("{RUN_DIR}")));
            exec.setMain(GRADLE_START_CLIENT);
            //exec.jvmArgs("-Xincgc", "-Xmx1024M", "-Xms1024M", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.args(getClientRunArgs());
            exec.workingDir(delayedFile("{RUN_DIR}"));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client");

            exec.dependsOn("makeStart");
        }

        exec = makeTask("runServer", JavaExec.class);
        {
            exec.doFirst(new MakeDirExist(delayedFile("{RUN_DIR}")));
            exec.setMain(GRADLE_START_SERVER);
            exec.jvmArgs("-Xincgc", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.workingDir(delayedFile("{RUN_DIR}"));
            exec.args(getServerRunArgs());
            exec.setStandardOutput(System.out);
            exec.setStandardInput(System.in);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft Server");

            exec.dependsOn("makeStart");
        }

        exec = makeTask("debugClient", JavaExec.class);
        {
            exec.doFirst(new MakeDirExist(delayedFile("{RUN_DIR}")));
            exec.doFirst( new Action() {
                @Override public void execute(Object o)
                {
                    project.getLogger().error("");
                    project.getLogger().error("THIS TASK WILL BE DEP RECATED SOON!");
                    project.getLogger().error("Instead use the runClient task, with the --debug-jvm option");
                    if (!project.getGradle().getGradleVersion().equals("1.12"))
                    {
                        project.getLogger().error("You may have to update to Gradle 1.12");
                    }
                    project.getLogger().error("");
                }
            });
            exec.setMain(GRADLE_START_CLIENT);
            exec.jvmArgs("-Xincgc", "-Xmx1024M", "-Xms1024M", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.args(getClientRunArgs());
            exec.workingDir(delayedFile("{RUN_DIR}"));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);
            exec.setDebug(true);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client in debug mode");

            exec.dependsOn("makeStart");
        }

        exec = makeTask("debugServer", JavaExec.class);
        {
            exec.doFirst(new MakeDirExist(delayedFile("{RUN_DIR}")));
            exec.doFirst( new Action() {
                @Override public void execute(Object o)
                {
                    project.getLogger().error("");
                    project.getLogger().error("THIS TASK WILL BE DEPRECATED SOON!");
                    project.getLogger().error("Instead use the runServer task, with the --debug-jvm option");
                    if (!project.getGradle().getGradleVersion().equals("1.12"))
                    {
                        project.getLogger().error("You may have to update to Gradle 1.12");
                    }
                    project.getLogger().error("");
                }
            });
            exec.setMain(GRADLE_START_SERVER);
            exec.jvmArgs("-Xincgc", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.workingDir(delayedFile("{RUN_DIR}"));
            exec.args(getServerRunArgs());
            exec.setStandardOutput(System.out);
            exec.setStandardInput(System.in);
            exec.setErrorOutput(System.err);
            exec.setDebug(true);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft serevr in debug mode");

            exec.dependsOn("makeStart");
        }
    }

    private final void createSourceCopyTasks()
    {
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        // do the special source moving...
        SourceCopyTask task;

        // main
        {
            DelayedFile dir = delayedFile(SOURCES_DIR + "/java");

            task = makeTask("sourceMainJava", SourceCopyTask.class);
            task.setSource(main.getJava());
            task.setOutput(dir);

            JavaCompile compile = (JavaCompile) project.getTasks().getByName(main.getCompileJavaTaskName());
            compile.dependsOn("sourceMainJava");
            compile.setSource(dir);
        }

        // scala!!!
        if (project.getPlugins().hasPlugin("scala"))
        {
            ScalaSourceSet set = (ScalaSourceSet) new DslObject(main).getConvention().getPlugins().get("scala");
            DelayedFile dir = delayedFile(SOURCES_DIR + "/scala");

            task = makeTask("sourceMainScala", SourceCopyTask.class);
            task.setSource(set.getScala());
            task.setOutput(dir);

            ScalaCompile compile = (ScalaCompile) project.getTasks().getByName(main.getCompileTaskName("scala"));
            compile.dependsOn("sourceMainScala");
            compile.setSource(dir);
        }

        // groovy!!!
        if (project.getPlugins().hasPlugin("groovy"))
        {
            GroovySourceSet set = (GroovySourceSet) new DslObject(main).getConvention().getPlugins().get("groovy");
            DelayedFile dir = delayedFile(SOURCES_DIR + "/groovy");

            task = makeTask("sourceMainGroovy", SourceCopyTask.class);
            task.setSource(set.getGroovy());
            task.setOutput(dir);

            GroovyCompile compile = (GroovyCompile) project.getTasks().getByName(main.getCompileTaskName("groovy"));
            compile.dependsOn("sourceMainGroovy");
            compile.setSource(dir);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public final void afterEvaluate()
    {
        String mcversion = getMcVersion(getExtension());
        if (!getExtension().mappingsSet() && mcversion.startsWith("1.8"))
        {
            getExtension().setMappings("snapshot_20141001"); //Default snapshots for 1.8
        }

        super.afterEvaluate();

        // version checks
        {
            String version = getMcVersion(getExtension());
            if (hasApiVersion())
                version = getApiVersion(getExtension());

            doVersionChecks(version);
        }

        // ensure plugin application sequence.. groovy or scala or wtvr first, then the forge/fml/liteloader plugins
        if (!hasScalaBefore && project.getPlugins().hasPlugin("scala"))
            throw new RuntimeException(delayedString("You have applied the 'scala' plugin after '{API_NAME}', you must apply it before.").call());
        if (!hasGroovyBefore && project.getPlugins().hasPlugin("groovy"))
            throw new RuntimeException(delayedString("You have applied the 'groovy' plugin after '{API_NAME}', you must apply it before.").call());

        project.getDependencies().add(CONFIG_USERDEV, delayedString(getUserDev()).call() + ":userdev");

        // grab the json && read dependencies
        if (getDevJson().call().exists())
        {
            readAndApplyJson(getDevJson().call(), CONFIG_DEPS, CONFIG_NATIVES, project.getLogger());
        }

        delayedTaskConfig();

        // add MC repo.
        final String repoDir = delayedDirtyFile("this", "doesnt", "matter").call().getParentFile().getAbsolutePath();
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addFlatRepo(proj, getApiName()+"FlatRepo", repoDir);
                proj.getLogger().debug("Adding repo to " + proj.getPath() + " >> " + repoDir);
            }
        });

        // check for decompilation status.. has decompiled or not etc
        final File decompFile = delayedDirtyFile(getSrcDepName(), CLASSIFIER_SOURCES, "jar").call();
        if (decompFile.exists())
        {
            getExtension().setDecomp();
        }

        // post decompile status thing.
        configurePostDecomp(getExtension().isDecomp(), false);

        {
            // stop getting empty dirs
            Action<ConventionTask> act = new Action() {
                @Override
                public void execute(Object arg0)
                {
                    Zip task = (Zip) arg0;
                    task.setIncludeEmptyDirs(false);
                }
            };

            project.getTasks().withType(Jar.class, act);
            project.getTasks().withType(Zip.class, act);
        }
    }

    /**
     * Allows for the configuration of tasks in AfterEvaluate
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void delayedTaskConfig()
    {
        // add extraSRG lines to reobf task
        {
            ReobfTask task = ((ReobfTask) project.getTasks().getByName("reobf"));
            task.reobf(project.getTasks().getByName("jar"), new Action<ArtifactSpec>()
            {
                @Override
                public void execute(ArtifactSpec arg0)
                {
                    JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
                    arg0.setClasspath(javaConv.getSourceSets().getByName("main").getCompileClasspath());
                }

            });
            task.setExtraSrg(getExtension().getSrgExtra());
        }

        // configure output of recompile task
        {
            JavaCompile compile = (JavaCompile) project.getTasks().getByName("recompMinecraft");
            compile.setDestinationDir(delayedFile(RECOMP_CLS_DIR).call());
        }

        // configure output of repackage task.
        {
            Jar repackageTask = (Jar) project.getTasks().getByName("repackMinecraft");
            final DelayedFile recomp = delayedDirtyFile(getSrcDepName(), null, "jar");

            //done in the delayed configuration.
            File out = recomp.call();
            repackageTask.setArchiveName(out.getName());
            repackageTask.setDestinationDir(out.getParentFile());
        }
        
        {
            // because different versions of authlib
            CreateStartTask task = (CreateStartTask) project.getTasks().getByName("makeStart");
            
            if (getMcVersion(getExtension()).startsWith("1.7")) // MC 1.7.X
            {
                if (getMcVersion(getExtension()).endsWith("10")) // MC 1.7.10
                {
                    task.addReplacement("//@@USERTYPE@@", "argMap.put(\"userType\", auth.getUserType().getName());");
                    task.addReplacement("//@@USERPROP@@", "argMap.put(\"userProperties\", new GsonBuilder().registerTypeAdapter(com.mojang.authlib.properties.PropertyMap.class, new net.minecraftforge.gradle.OldPropertyMapSerializer()).create().toJson(auth.getUserProperties()));");
                }
                else
                {
                    task.removeResource("net/minecraftforge/gradle/OldPropertyMapSerializer.java");
                }
            }
            else // MC 1.8 +
            {
                task.removeResource("net/minecraftforge/gradle/OldPropertyMapSerializer.java");
                task.addReplacement("//@@USERTYPE@@", "argMap.put(\"userType\", auth.getUserType().getName());");
                task.addReplacement("//@@USERPROP@@", "argMap.put(\"userProperties\", new GsonBuilder().registerTypeAdapter(com.mojang.authlib.properties.PropertyMap.class, new com.mojang.authlib.properties.PropertyMap.Serializer()).create().toJson(auth.getUserProperties()));");
            }
        }

        // Add the mod and stuff to the classpath of the exec tasks.
        final Jar jarTask = (Jar) project.getTasks().getByName("jar");

        JavaExec exec = (JavaExec) project.getTasks().getByName("runClient");
        {
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
        }

        exec = (JavaExec) project.getTasks().getByName("runServer");
        {
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
        }

        exec = (JavaExec) project.getTasks().getByName("debugClient");
        {
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
        }

        exec = (JavaExec) project.getTasks().getByName("debugServer");
        {
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
        }

        // configure source replacement.
        for (SourceCopyTask t : project.getTasks().withType(SourceCopyTask.class))
        {
            t.replace(getExtension().getReplacements());
            t.include(getExtension().getIncludes());
        }

        // use zinc for scala compilation
        project.getTasks().withType(ScalaCompile.class, new Action() {
            @Override
            public void execute(Object arg0)
            {
                ((ScalaCompile) arg0).getScalaCompileOptions().setUseAnt(false);
            }
        });
    }

    /**
     * Configure tasks and stuff after you know if the decomp file exists or not.
     * @param decomp will decompile this task
     * @param remove should remove old dependencies or not
     */
    protected void configurePostDecomp(boolean decomp, boolean remove)
    {
        if (decomp)
        {
            ((ReobfTask) project.getTasks().getByName("reobf")).setDeobfFile(((ProcessJarTask) project.getTasks().getByName("deobfuscateJar")).getDelayedOutput());
            ((ReobfTask) project.getTasks().getByName("reobf")).setRecompFile(delayedDirtyFile(getSrcDepName(), null, "jar"));
        }
        else
        {
            (project.getTasks().getByName("compileJava")).dependsOn("deobfBinJar");
            (project.getTasks().getByName("compileApiJava")).dependsOn("deobfBinJar");
        }

        setMinecraftDeps(decomp, remove);
        
        if (decomp && remove)
        {
            (project.getTasks().getByName("deobfBinJar")).onlyIf(Constants.CALL_FALSE);
        }
    }

    protected void setMinecraftDeps(boolean decomp, boolean remove)
    {
        String version = getMcVersion(getExtension());
        if (hasApiVersion())
            version = getApiVersion(getExtension());


        if (decomp)
        {
            project.getDependencies().add(CONFIG_MC, ImmutableMap.of("name", getSrcDepName(), "version", version));
            if (remove)
            {
                project.getConfigurations().getByName(CONFIG_MC).exclude(ImmutableMap.of("module", getBinDepName()));
            }
        }
        else
        {
            project.getDependencies().add(CONFIG_MC, ImmutableMap.of("name", getBinDepName(), "version", version));
            if (remove)
            {
                project.getConfigurations().getByName(CONFIG_MC).exclude(ImmutableMap.of("module", getSrcDepName()));
            }
        }
    }

    /**
     * Add Forge/FML ATs here.
     * This happens during normal evaluation, and NOT AfterEvaluate.
     * @param task the deobfuscation task
     */
    protected abstract void configureDeobfuscation(ProcessJarTask task);

    /**
     *
     * @param version may have pre-release suffix _pre#
     */
    protected abstract void doVersionChecks(String version);

    /**
     * Returns a file in the DirtyDir if the deobfuscation task is dirty. Otherwise returns the cached one.
     * @param name the name..
     * @param classifier the classifier
     * @param ext the extension
     * @return delayed file
     */
    protected DelayedFile delayedDirtyFile(final String name, final String classifier, final String ext)
    {
        return delayedDirtyFile(name, classifier, ext, true);
    }

    /**
     * Returns a file in the DirtyDir if the deobfuscation task is dirty. Otherwise returns the cached one.
     * @param name the name..
     * @param classifier the classifier
     * @param ext the extension
     * @param usesMappings whether or not MCP mappings are specified
     * @return delayed file
     */
    @SuppressWarnings("serial")
    protected DelayedFile delayedDirtyFile(final String name, final String classifier, final String ext, final boolean usesMappings)
    {
        return new DelayedFile(project, "", this) {
            @Override
            public File resolveDelayed()
            {
                ProcessJarTask decompDeobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
                pattern = (decompDeobf.isClean() ? "{API_CACHE_DIR}/"+(usesMappings ? MAPPING_APPENDAGE : "") : DIRTY_DIR) + "/";

                if (!Strings.isNullOrEmpty(name))
                    pattern += name;
                else
                    pattern += "{API_NAME}";

                pattern += "-" + (hasApiVersion() ? "{API_VERSION}" : "{MC_VERSION}");

                if (!Strings.isNullOrEmpty(classifier))
                    pattern+= "-"+classifier;
                if (!Strings.isNullOrEmpty(ext))
                    pattern+= "."+ext;

                return super.resolveDelayed();
            }
        };
    }

    @SuppressWarnings("unchecked")
    protected Class<T> getExtensionClass()
    {
        return (Class<T>) UserExtension.class;
    }

    private void addGitIgnore()
    {
        File git = new File(project.getBuildDir(), ".gitignore");
        if (!git.exists())
        {
            git.getParentFile().mkdir();
            try
            {
                Files.write("#Seriously guys, stop commiting this to your git repo!\r\n*".getBytes(), git);
            }
            catch (IOException e){}
        }
    }
}

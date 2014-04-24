package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.ASSETS;
import static net.minecraftforge.gradle.common.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import joptsimple.internal.Strings;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.tasks.CopyAssetsTask;
import net.minecraftforge.gradle.tasks.DecompileTask;
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
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.eclipse.model.*;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class UserBasePlugin<T extends UserExtension> extends BasePlugin<T>
{
    @Override
    public void applyPlugin()
    {
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("maven");
        this.applyExternalPlugin("eclipse");
        this.applyExternalPlugin("idea");
        
        hasScalaBefore = project.getPlugins().hasPlugin("scala");
        hasGroovyBefore = project.getPlugins().hasPlugin("groovy");
        
        configureDeps();
        configureCompilation();
        fixEclipseNatives();
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
        task.dependsOn("genSrgs", "deobfBinJar", "copyAssets", "extractNatives");
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");
        //configureDevSetup(task);

        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "copyAssets", "extractNatives", "repackMinecraft");
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");
        //configureDecompSetup(task);
    }
    
    private boolean hasAppliedJson = false;
    private boolean hasScalaBefore = false;
    private boolean hasGroovyBefore = false;
    
    /**
     * may not include delayed tokens.
     */
    protected abstract String getApiName();
    /**
     * Name of the source dependency.  eg: forgeSrc
     * may not include delayed tokens.
     */
    protected abstract String getSrcDepName();
    /**
     * Name of the source dependency.  eg: forgeBin
     * may not include delayed tokens.
     */
    protected abstract String getBinDepName();
    
    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     */
    protected abstract boolean hasApiVersion();
    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     */
    protected abstract String getApiVersion(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * may not include delayed tokens.
     */
    protected abstract String getMcVersion(T exten);
    /**
     * May invoke the extension object, or be hardcoded.
     * This unlike the others, is evaluated as a delayed file, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     */
    protected abstract String getApiCacheDir(T exten);
    /**
     * This unlike the others, is evaluated as a delayed string, and may contain various tokens including:
     * {API_NAME} {API_VERSION} {MC_VERSION}
     */
    protected abstract String getUserDev();
    
    /**
     * For run configurations
     */
    protected abstract String getClientRunClass();
    /**
     * For run configurations
     */
    protected abstract Iterable<String> getClientRunArgs();
    /**
     * For run configurations
     */
    protected abstract String getServerRunClass();
    /**
     * For run configurations
     */
    protected abstract Iterable<String> getServerRunArgs();
    
//    protected abstract void configureCISetup(Task task);
//    protected abstract void configureDevSetup(Task task);
//    protected abstract void configureDecompSetup(Task task);
    
    @Override
    public String resolve(String pattern, Project project, T exten)
    {
        pattern = pattern.replace("{API_CACHE_DIR}", this.getApiCacheDir(exten));
        pattern = pattern.replace("{MC_VERSION}", getMcVersion(exten));
        pattern = super.resolve(pattern, project, exten);
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
        project.getConfigurations().create(CONFIG_DEPS);
        project.getConfigurations().create(CONFIG_MC);

        // special userDev stuff
        ExtractTask extractUserDev = makeTask("extractUserDev", ExtractTask.class);
        extractUserDev.into(delayedFile(PACK_DIR));
        extractUserDev.setDoesCache(true);
        extractUserDev.doLast(new Action<Task>()
        {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(getDevJson().call(), CONFIG_DEPS, CONFIG_NATIVES, arg0.getLogger());
            }
        });

        // special native stuff
        ExtractTask extractNatives = makeTask("extractNatives", ExtractTask.class);
        extractNatives.into(delayedFile(NATIVES_DIR));
        extractNatives.dependsOn("extractUserDev");

        // extra libs folder.
        project.getDependencies().add("compile", project.fileTree("libs"));

        // make MC dependencies into normal compile classpath
        project.getDependencies().add("compile", project.getConfigurations().getByName(CONFIG_DEPS));
        project.getDependencies().add("compile", project.getConfigurations().getByName(CONFIG_MC));
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
    }
    
    private void readAndApplyJson(File file, String depConfig, String nativeConfig, Logger log)
    {
        if (version == null)
        {
            try
            {
                version = JsonFactory.loadVersion(file);
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
            log.info("RESOLVED: " + depConfig);

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
            log.info("RESOLVED: " + nativeConfig);

        hasAppliedJson = true;

        // add stuff to the natives task thing..
        // extract natives
        ExtractTask task = (ExtractTask) project.getTasks().findByName("extractNatives");
        for (File dep : project.getConfigurations().getByName(CONFIG_NATIVES).getFiles())
        {
            log.info("ADDING NATIVE: " + dep.getPath());
            task.from(delayedFile(dep.getAbsolutePath()));
            task.exclude("META-INF/**", "META-INF/**");
        }
    }

    @SuppressWarnings({ "unchecked" })
    protected void fixEclipseNatives()
    {
        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");

        eclipseConv.getClasspath().setDownloadJavadoc(true);
        eclipseConv.getClasspath().setDownloadSources(true);
        ((ActionBroadcast<Classpath>) eclipseConv.getClasspath().getFile().getWhenMerged()).add(new Action<Classpath>()
        {
            @Override
            public void execute(Classpath classpath)
            {
                String natives = delayedString(NATIVES_DIR).call().replace('\\', '/');
                for (ClasspathEntry e : classpath.getEntries())
                {
                    if (e instanceof Library)
                    {
                        Library lib = (Library) e;
                        if (lib.getPath().contains("lwjg") || lib.getPath().contains("jinput"))
                        {
                            lib.setNativeLibraryLocation(natives);
                        }
                    }
                }
            }
        });

        Task task = makeTask("afterEclipseImport", DefaultTask.class);
        task.doLast(new Action<Object>() {
            public void execute(Object obj)
            {
                try
                {
                    Node root = new XmlParser().parseText(Files.toString(project.file(".classpath"), Charset.defaultCharset()));

                    HashMap<String, String> map = new HashMap<String, String>();
                    map.put("name", "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY");
                    map.put("value", delayedString(NATIVES_DIR).call());

                    for (Node child : (List<Node>) root.children())
                    {
                        if (child.attribute("path").equals("org.springsource.ide.eclipse.gradle.classpathcontainer"))
                        {
                            child.appendNode("attributes").appendNode("attribute", map);
                            break;
                        }
                    }

                    String result = XmlUtil.serialize(root);

                    project.getLogger().lifecycle(result);
                    Files.write(result, project.file(".classpath"), Charset.defaultCharset());

                }
                catch (Exception e)
                {
                    e.printStackTrace();
                    return;
                }
            }
        });
    }
    
    @SuppressWarnings("serial")
    protected void configureIntellij()
    {
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        ideaConv.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea").getFiles());
        ideaConv.getModule().setDownloadJavadoc(true);
        ideaConv.getModule().setDownloadSources(true);

        Task task = makeTask("genIntellijRuns", DefaultTask.class);
        task.doLast(new Action<Task>() {
            @Override
            public void execute(Task task)
            {
                try
                {
                    String module = task.getProject().getProjectDir().getCanonicalPath();
                    File file = project.file(".idea/workspace.xml");
                    if (!file.exists())
                        throw new RuntimeException("Only run this task after importing a build.gradle file into intellij!");

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

        String natives = delayedFile(NATIVES_DIR).call().getCanonicalPath().replace(module, "$PROJECT_DIR$");

        String[][] config = new String[][]
        {
                new String[]
                {
                        "Minecraft Client",
                        getClientRunClass(),
                        "-Xincgc -Xmx1024M -Xms1024M -Djava.library.path=\"" + natives + "\" -Dfml.ignoreInvalidMinecraftCertificates=true",
                        Joiner.on(' ').join(getClientRunArgs())
                },
                new String[]
                {
                        "Minecraft Server",
                        getServerRunClass(),
                        "-Xincgc -Dfml.ignoreInvalidMinecraftCertificates=true",
                        Joiner.on(' ').join(getClientRunArgs())
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
            add(child, "option", "name", "WORKING_DIRECTORY", "value", "file://" + delayedFile("{ASSET_DIR}").call().getParentFile().getCanonicalPath().replace(module, "$PROJECT_DIR$"));
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
            CopyAssetsTask task = makeTask("copyAssets", CopyAssetsTask.class);
            task.setAssetsDir(delayedFile(ASSETS));
            task.setOutputDir(delayedFile("{ASSET_DIR}"));
            task.setAssetIndex(getAssetIndexClosure());
            task.dependsOn("getAssets");
        }
        
        {
            GenSrgTask task = makeTask("genSrgs", GenSrgTask.class);
            task.setInSrg(delayedFile(PACKAGED_SRG));
            task.setInExc(delayedFile(PACKAGED_EXC));
            task.setMethodsCsv(delayedFile(METHOD_CSV));
            task.setFieldsCsv(delayedFile(FIELD_CSV));
            task.setNotchToSrg(delayedFile(DEOBF_SRG_SRG));
            task.setNotchToMcp(delayedFile(DEOBF_MCP_SRG));
            task.setMcpToSrg(delayedFile(REOBF_SRG));
            task.setMcpToNotch(delayedFile(REOBF_NOTCH_SRG));
            task.setSrgExc(delayedFile(EXC_SRG));
            task.setMcpExc(delayedFile(EXC_MCP));
            task.dependsOn("extractUserDev");
        }
        
        {
            MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
            task.setClient(delayedFile(JAR_CLIENT_FRESH));
            task.setServer(delayedFile(JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(JAR_MERGED));
            task.setMergeCfg(delayedFile(MERGE_CFG));
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
            task.setOutCleanJar(delayedFile("{API_CACHE_DIR}/" + name));
            task.setOutDirtyJar(delayedFile(DIRTY_DIR + "/" + name));
            task.setApplyMarkers(false);
            task.setStripSynthetics(true);
            configureDeobfuscation(task);
            task.dependsOn("downloadMcpTools", "mergeJars", "genSrgs", "applyBinPatches");
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
            task.reobf(project.getTasks().getByName("jar"), new Action<ArtifactSpec>()
            {
                @Override
                public void execute(ArtifactSpec arg0)
                {
                    JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
                    arg0.setClasspath(javaConv.getSourceSets().getByName("main").getCompileClasspath());
                }

            });
            project.getTasks().getByName("assemble").dependsOn(task);
        }
        
        createPostDecompTasks();
        createExecTasks();
        createSourceCopyTasks();
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private final void createPostDecompTasks()
    {
        DelayedFile decompOut = delayedDirtyFile(null, CLASSIFIER_DECOMPILED, "jar");
        DelayedFile remapped = delayedDirtyFile(getSrcDepName(), CLASSIFIER_SOURCES, "jar");
        final DelayedFile recomp = delayedDirtyFile(getSrcDepName(), null, "jar");
        final DelayedFile recompSrc = delayedFile(RECOMP_SRC_DIR);
        final DelayedFile recompCls = delayedFile(RECOMP_CLS_DIR);
        
        DecompileTask decomp = makeTask("decompile", DecompileTask.class);
        {
            decomp.setInJar(delayedDirtyFile(null, CLASSIFIER_DEOBF_SRG, "jar"));
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
    
    private final void createExecTasks()
    {
        JavaExec exec = makeTask("runClient", JavaExec.class);
        {
            exec.setMain(getClientRunClass());
            exec.jvmArgs("-Xincgc", "-Xmx1024M", "-Xms1024M", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.args(getClientRunArgs());
            exec.workingDir(delayedFile("{ASSET_DIR}/.."));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client");
        }

        exec = makeTask("runServer", JavaExec.class);
        {
            exec.setMain(getServerRunClass());
            exec.jvmArgs("-Xincgc", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.workingDir(delayedFile("{ASSET_DIR}/.."));
            exec.args(getServerRunArgs());
            exec.setStandardOutput(System.out);
            exec.setStandardInput(System.in);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft Server");
        }

        exec = makeTask("debugClient", JavaExec.class);
        {
            exec.setMain(getClientRunClass());
            exec.jvmArgs("-Xincgc", "-Xmx1024M", "-Xms1024M", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.args(getClientRunArgs());
            exec.workingDir(delayedFile("{ASSET_DIR}/.."));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);
            exec.setDebug(true);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client in debug mode");
        }

        exec = makeTask("debugServer", JavaExec.class);
        {
            exec.setMain(getServerRunClass());
            exec.jvmArgs("-Xincgc", "-Dfml.ignoreInvalidMinecraftCertificates=true");
            exec.workingDir(delayedFile("{ASSET_DIR}/.."));
            exec.args(getServerRunArgs());
            exec.setStandardOutput(System.out);
            exec.setStandardInput(System.in);
            exec.setErrorOutput(System.err);
            exec.setDebug(true);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft serevr in debug mode");
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
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
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
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
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
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
            task.setOutput(dir);

            GroovyCompile compile = (GroovyCompile) project.getTasks().getByName(main.getCompileTaskName("groovy"));
            compile.dependsOn("sourceMainGroovy");
            compile.setSource(dir);
        }
    }
    
    @Override
    public final void afterEvaluate()
    {
        super.afterEvaluate();
        
        {
            // version checks
            String apiVersion = getApiVersion(getExtension());
            int buildNumber = Integer.parseInt(apiVersion.substring(apiVersion.lastIndexOf('.') + 1));
            doVersionChecks(buildNumber);
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
        
        // check for decompilation status.. has decompiled or not etc
        final File decompFile = delayedDirtyFile(getSrcDepName(), CLASSIFIER_SOURCES, "jar").call();
        if (decompFile.exists())
        {
            getExtension().setDecomp();
        }
        
        // post decompile status thing.
        configurePostDecomp(getExtension().isDecomp());
    }
    
    /**
     * Allows for the configuration of tasks in AfterEvaluate
     */
    protected void delayedTaskConfig()
    {
        // configure userDev extraction
        // extract userdev
        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(CONFIG_USERDEV).getSingleFile().getAbsolutePath()));
        project.getTasks().findByName("getAssetsIndex").dependsOn("extractUserDev");

        // add extraSRG lines to reobf task
        ((ReobfTask) project.getTasks().getByName("reobf")).setExtraSrg(getExtension().getSrgExtra());
        
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
        
        // Add the mod and stuff to the classpath of the exec tasks.
        final Jar jarTask = (Jar) project.getTasks().getByName("jar");
        
        JavaExec exec = (JavaExec) project.getTasks().getByName("runClient");
        {
            exec.jvmArgs("-Djava.library.path=" + delayedFile(NATIVES_DIR).call().getAbsolutePath());
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
            exec.jvmArgs("-Djava.library.path=" + delayedFile(NATIVES_DIR).call().getAbsolutePath());
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
    }
    
    /**
     * Configure tasks and stuff after you know if the decomp file exists or not. 
     */
    protected void configurePostDecomp(boolean decomp)
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
    }
    
    /**
     * Add Forge/FML ATs here.
     * This happens during normal evaluation, and NOT AfterEvaluate.
     */
    protected abstract void configureDeobfuscation(ProcessJarTask task);
    
    protected abstract void doVersionChecks(int buildNumber);
    
    /**
     * Returns a file in the DirtyDir if the deobfusctaion task is dirty. Otherwise returns the cached one.
     * @param classifier
     * @param ext
     * @return
     */
    @SuppressWarnings("serial")
    protected DelayedFile delayedDirtyFile(final String name, final String classifier, final String ext)
    {
        return new DelayedFile(project, "", this) {
            @Override
            public File call()
            {
                ProcessJarTask decompDeobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
                pattern = (decompDeobf.isClean() ? "{API_CACHE_DIR}" : DIRTY_DIR) + "/";
                
                if (!Strings.isNullOrEmpty(name))
                    pattern += name;
                else
                    pattern += "{API_NAME}";
                
                pattern += "-" + (hasApiVersion() ? "{API_VERSION}" : "{MC_VERSION}");
                
                if (!Strings.isNullOrEmpty(classifier))
                    pattern+= "-"+classifier;
                if (!Strings.isNullOrEmpty(ext))
                    pattern+= "."+ext;
                
                return super.call();
            }
        };
    }
}

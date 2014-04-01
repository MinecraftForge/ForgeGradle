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
import java.io.FileOutputStream;
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

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.tasks.CopyAssetsTask;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;
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
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class UserBasePlugin extends BasePlugin<UserExtension>
{
    private boolean hasApplied = false;

    @SuppressWarnings("serial")
    @Override
    public void applyPlugin()
    {
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("maven");
        this.applyExternalPlugin("eclipse");
        this.applyExternalPlugin("idea");

        configureDeps();
        configureCompilation();
        configureEclipse();
        configureIntellij();

        tasks();

        Task task = makeTask("setupCIWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar");
        task.setDescription("Sets up the bare minimum to build a minecraft mod. Idea for CI servers");
        task.setGroup("ForgeGradle");

        task = makeTask("setupDevWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar", "copyAssets", "extractNatives");
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");

        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");

        project.getTasks().getByName("reobf").dependsOn("genSrgs");
        
        // stop people screwing stuff up.
        project.getGradle().getTaskGraph().whenReady(new Closure<Object>(this, null) {
            @Override
            public Object call()
            {
                TaskExecutionGraph graph = project.getGradle().getTaskGraph();
                String path = project.getPath();
                
                boolean hasSetup = graph.hasTask(path + "setupDecompWorkspace"); 
                boolean hasBuild = graph.hasTask(path + "eclipse") || graph.hasTask(path + "ideaModule") || graph.hasTask(path + "build"); 
                
                if (hasSetup && hasBuild)
                    throw new RuntimeException("You are running the setupDecompWorkspace task and an IDE/build task in the same command. Do them seperately.");
                
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

    private void checkDecompStatus()
    {
        final boolean hasAts = !((ProcessJarTask) project.getTasks().getByName("deobfBinJar")).isClean();
        final File decompFile = delayedFile((hasAts ? DIRTY_DIR : getCacheDir()) + getDecompOut()).call();
        getExtension().isDecomp = decompFile.exists();
    }

    protected Class<UserExtension> getExtensionClass()
    {
        return UserExtension.class;
    }

    @Override
    protected String getDevJson()
    {
        return DelayedBase.resolve(JSON, project);
    }

    private void tasks()
    {
        {
            MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
            task.setClient(df(JAR_CLIENT_FRESH));
            task.setServer(df(JAR_SERVER_FRESH));
            task.setOutJar(df(JAR_MERGED));
            task.setMergeCfg(df(MERGE_CFG));
            task.dependsOn("extractUserDev", "downloadClient", "downloadServer");
        }

        {
            GenSrgTask task = makeTask("genSrgs", GenSrgTask.class);
            task.setInSrg(df(PACKAGED_SRG));
            task.setInExc(df(PACKAGED_EXC));
            task.setMethodsCsv(df(METHOD_CSV));
            task.setFieldsCsv(df(FIELD_CSV));
            task.setNotchToSrg(df(DEOBF_SRG_SRG));
            task.setNotchToMcp(df(DEOBF_MCP_SRG));
            task.setMcpToSrg(df(REOBF_SRG));
            task.setMcpToNotch(df(REOBF_NOTCH_SRG));
            task.setSrgExc(df(EXC_SRG));
            task.setMcpExc(df(EXC_MCP));
            task.dependsOn("extractUserDev");
        }

        {
            ApplyBinPatchesTask task = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
            task.setInJar(df(JAR_MERGED));
            task.setOutJar(getBinPatchOut());
            task.setPatches(df(BINPATCHES));
            task.setClassesJar(df(BINARIES_JAR));
            task.setResources(delayedFileTree(RES_DIR));
            task.dependsOn("mergeJars");
        }

        {
            ProcessJarTask task = makeTask("deobfBinJar", ProcessJarTask.class);
            task.setSrg(df(DEOBF_MCP_SRG));
            task.setExceptorJson(df(EXC_JSON));
            task.setExceptorCfg(df(EXC_MCP));
            task.setFieldCsv(df(FIELD_CSV));
            task.setMethodCsv(df(METHOD_CSV));
            task.setApplyMarkers(false);
            addATs(task);
            task.dependsOn("downloadMcpTools", "mergeJars", "genSrgs", "applyBinPatches");
        }

        {
            ProcessJarTask task = makeTask("deobfuscateJar", ProcessJarTask.class);
            task.setSrg(df(DEOBF_SRG_SRG));
            task.setInJar(df(JAR_MERGED));
            task.setExceptorJson(df(EXC_JSON));
            task.setExceptorCfg(df(EXC_SRG));
            task.setApplyMarkers(true);
            addATs(task);
            task.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }

        {
            ReobfTask task = makeTask("reobf", ReobfTask.class);
            task.dependsOn("genSrgs");
            task.setExceptorCfg(delayedFile(EXC_SRG));
            task.setSrg(delayedFile(REOBF_SRG));
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
            if (getExtension().isDecomp())
            {
                task.setFieldCsv(delayedFile(FIELD_CSV));
                task.setFieldCsv(delayedFile(METHOD_CSV));
            }
        }

        {
            CopyAssetsTask task = makeTask("copyAssets", CopyAssetsTask.class);
            task.setAssetsDir(delayedFile(ASSETS));
            task.setOutputDir(delayedFile("{ASSET_DIR}"));
            task.setAssetIndex(getAssetIndexClosure());
            task.dependsOn("getAssets");
        }
    }

    private void delayedTasks()
    {
        ProcessJarTask deobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        boolean clean = deobf.isClean();
        DelayedFile decompOut = delayedFile((clean ? getCacheDir() : DIRTY_DIR) + getDecompOut());

        {
            DecompileTask task = makeTask("decompile", DecompileTask.class);
            task.setInJar(deobf.getDelayedOutput());
            task.setOutJar(decompOut);
            task.setFernFlower(delayedFile(FERNFLOWER));
            task.setPatch(delayedFile(MCP_PATCH_DIR));
            task.setAstyleConfig(delayedFile(ASTYLE_CFG));
            task.dependsOn("downloadMcpTools", "deobfuscateJar", "genSrgs");
        }

        doPostDecompTasks(clean, decompOut);
        createMcModuleDep(clean, project.getDependencies(), CONFIG);
        
        // get sourceSet
        Jar jarTask = (Jar) project.getTasks().getByName("jar");
        
        JavaExec exec = makeTask("runClient", JavaExec.class);
        {
        	exec.classpath(project.getConfigurations().getByName("runtime"));
        	exec.classpath(jarTask.getArchivePath());
        	exec.setMain("net.minecraft.launchwrapper.Launch");
        	exec.jvmArgs("-Xincgc", "-Xmx1024M", "-Xms1024M", "-Dfml.ignoreInvalidMinecraftCertificates=true");
        	exec.jvmArgs("-Djava.library.path=" + delayedFile(NATIVES_DIR).call().getAbsolutePath());
        	exec.args("--version 1.7", "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker", "--username=ForgeDevName", "--accessToken", "FML");
        	exec.setWorkingDir(delayedFile("{ASSET_DIR}").call().getParentFile());
        	exec.setStandardOutput(System.out);
        	exec.setErrorOutput(System.err);
        	
        	exec.dependsOn(jarTask);
        }
        
        exec = makeTask("runServer", JavaExec.class);
        {
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(jarTask.getArchivePath());
        	exec.setMain("cpw.mods.fml.relauncher.ServerLaunchWrapper");
        	exec.jvmArgs("-Xincgc", "-Dfml.ignoreInvalidMinecraftCertificates=true");
        	exec.setWorkingDir(delayedFile("{ASSET_DIR}").call().getParentFile());
        	exec.setStandardOutput(System.out);
        	exec.setStandardInput(System.in);
        	exec.setErrorOutput(System.err);
        	
        	exec.dependsOn(jarTask);
        }
    }

    protected abstract void doPostDecompTasks(boolean isClean, DelayedFile decompOut);

    protected abstract DelayedFile getBinPatchOut();

    protected abstract String getDecompOut();

    protected abstract String getCacheDir();

    protected abstract void addATs(ProcessJarTask task);

    protected abstract void createMcModuleDep(boolean isClean, DependencyHandler depHandler, String depConfig);

    private void configureDeps()
    {
        // create configs
        project.getConfigurations().create(CONFIG_USERDEV);
        project.getConfigurations().create(CONFIG_NATIVES);
        project.getConfigurations().create(CONFIG_DEPS);
        project.getConfigurations().create(CONFIG);

        // special userDev stuff
        ExtractTask extractUserDev = makeTask("extractUserDev", ExtractTask.class);
        extractUserDev.into(delayedFile(PACK_DIR));
        extractUserDev.setDoesCache(true);
        extractUserDev.doLast(new Action<Task>()
        {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(delayedFile(JSON).call(), CONFIG_DEPS, CONFIG_NATIVES, arg0.getLogger());
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
        project.getDependencies().add("compile", project.getConfigurations().getByName(CONFIG));
    }

    protected void configureCompilation()
    {
        // get conventions
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = javaConv.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        SourceSet api = javaConv.getSourceSets().create("api");

        // set the Source
        javaConv.setSourceCompatibility("1.6");
        javaConv.setTargetCompatibility("1.6");

        // add to SourceSet compile paths
        api.setCompileClasspath(api.getCompileClasspath().plus(main.getCompileClasspath()));
        main.setCompileClasspath(main.getCompileClasspath().plus(api.getOutput()));
        test.setCompileClasspath(test.getCompileClasspath().plus(api.getOutput()).plus(main.getCompileClasspath()));

        // add sourceDirs to Intellij
        ideaConv.getModule().getSourceDirs().addAll(main.getAllSource().getFiles());
        ideaConv.getModule().getSourceDirs().addAll(test.getAllSource().getFiles());
        ideaConv.getModule().getSourceDirs().addAll(api.getAllSource().getFiles());

        project.getConfigurations().getByName("apiCompile").extendsFrom(project.getConfigurations().getByName("compile"));
    }

    private void doSourceReplacement()
    {
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        // do the special source moving...
        SourceCopyTask task;

        // main
        {
            task = makeTask("sourceMainJava", SourceCopyTask.class);
            task.setSource(main.getJava());
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
            task.setOutput(delayedFile(SOURCES_DIR + "/java"));

            JavaCompile compile = (JavaCompile) project.getTasks().getByName(main.getCompileJavaTaskName());
            compile.dependsOn("sourceMainJava");
            compile.setSource(task.getOutput());
        }

        // scala!!!
        if (project.getPlugins().hasPlugin("scala"))
        {
            ScalaSourceSet set = (ScalaSourceSet) new DslObject(main).getConvention().getPlugins().get("scala");

            task = makeTask("sourceMainScala", SourceCopyTask.class);
            task.setSource(set.getScala());
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
            task.setOutput(delayedFile(SOURCES_DIR + "/scala"));

            ScalaCompile compile = (ScalaCompile) project.getTasks().getByName(main.getCompileTaskName("scala"));
            compile.dependsOn("sourceMainScala");
            compile.setSource(task.getOutput());
        }

        // groovy!!!
        if (project.getPlugins().hasPlugin("groovy"))
        {
            GroovySourceSet set = (GroovySourceSet) new DslObject(main).getConvention().getPlugins().get("groovy");

            task = makeTask("sourceMainGroovy", SourceCopyTask.class);
            task.setSource(set.getGroovy());
            task.replace(getExtension().getReplacements());
            task.include(getExtension().getIncludes());
            task.setOutput(delayedFile(SOURCES_DIR + "/groovy"));

            GroovyCompile compile = (GroovyCompile) project.getTasks().getByName(main.getCompileTaskName("groovy"));
            compile.dependsOn("sourceMainGroovy");
            compile.setSource(task.getOutput());
        }
    }

    @SuppressWarnings({ "unchecked" })
    protected void configureEclipse()
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

        ideaConv.getModule().getExcludeDirs().addAll(project.files(".gradle", "build").getFiles());
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
                        "net.minecraft.launchwrapper.Launch",
                        "-Xincgc -Xmx1024M -Xms1024M -Djava.library.path=\"" + natives + "\" -Dfml.ignoreInvalidMinecraftCertificates=true",
                        "--version 1.7 --tweakClass cpw.mods.fml.common.launcher.FMLTweaker --username=ForgeDevName --accessToken FML"
                },
                new String[]
                {
                        "Minecraft Server",
                        "cpw.mods.fml.relauncher.ServerLaunchWrapper",
                        "-Xincgc -Dfml.ignoreInvalidMinecraftCertificates=true",
                        ""
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

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();

        // grab the json && read dependencies
        if (delayedFile(JSON).call().exists())
        {
            readAndApplyJson(delayedFile(JSON).call(), CONFIG_DEPS, CONFIG_NATIVES, project.getLogger());
        }

        // extract userdev
        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(CONFIG_USERDEV).getSingleFile().getAbsolutePath()));
        project.getTasks().findByName("getAssetsIndex").dependsOn("extractUserDev");

        // add extraSRG lines
        ((ReobfTask) project.getTasks().getByName("reobf")).setExtraSrg(getExtension().getSrgExtra());

        // add src ATs
        ProcessJarTask binDeobf = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        ProcessJarTask decompDeobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addTransformer(extAts);
        decompDeobf.addTransformer(extAts);

        // from the resources dirs
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            SourceSet main = javaConv.getSourceSets().getByName("main");
            SourceSet api = javaConv.getSourceSets().getByName("api");

            for (File at : main.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in main resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }

            for (File at : api.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in api resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }
        }
        
        // check for decompilation status.. has decompiled or not etc
        checkDecompStatus();

        // make delayed tasks regarding decompilation
        delayedTasks();

        // configure source replacement
        doSourceReplacement();

        // fix eclipse project location...
        fixEclipseProject(ECLIPSE_LOCATION);
    }
    
    @Override
    public void finalCall()
    {
        //final boolean isClean = !((ProcessJarTask) project.getTasks().getByName("deobfBinJar")).isClean();
        final boolean isDecomp = getExtension().isDecomp;
        
        if (isDecomp)
        {
            // its assumed that the dev workspace has already been run.
            ((ReobfTask) project.getTasks().getByName("reobf")).setDeobfFile(((ProcessJarTask) project.getTasks().getByName("deobfuscateJar")).getDelayedOutput());
        }
        else
        {
            project.getTasks().getByName("compileJava").dependsOn("deobfBinJar");
            project.getTasks().getByName("compileApiJava").dependsOn("deobfBinJar");
        }
    }

    private static final byte[] LOCATION_BEFORE = new byte[] { 0x40, (byte) 0xB1, (byte) 0x8B, (byte) 0x81, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x25, (byte) 0x96, (byte) 0xE7, (byte) 0xA3, (byte) 0x93, (byte) 0xBE, 0x1E };
    private static final byte[] LOCATION_AFTER  = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x58, (byte) 0xFB, (byte) 0xF3, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x51, (byte) 0xF3, (byte) 0x8C, 0x7B, (byte) 0xBB, 0x77, (byte) 0xC6 };

    protected void fixEclipseProject(String path)
    {
        File f = new File(path);
        if (f.exists())// && f.length() == 0)
        {
            String projectDir = "URI//" + project.getProjectDir().toURI().toString();
            try
            {
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(LOCATION_BEFORE); //Unknown but w/e
                fos.write((byte) ((projectDir.length() & 0xFF) >> 8));
                fos.write((byte) ((projectDir.length() & 0xFF) >> 0));
                fos.write(projectDir.getBytes());
                fos.write(LOCATION_AFTER); //Unknown but w/e
                fos.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
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

        if (hasApplied)
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

        hasApplied = true;

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

    @Override
    public String resolve(String pattern, Project project, UserExtension exten)
    {
        pattern = pattern.replace("{API_CACHE_DIR}", this.getCacheDir());
        pattern = super.resolve(pattern, project, exten);
        pattern = pattern.replace("{API_VERSION}", exten.getApiVersion());
        return pattern;
    }

    private DelayedFile df(String file)
    {
        return delayedFile(file);
    }
}

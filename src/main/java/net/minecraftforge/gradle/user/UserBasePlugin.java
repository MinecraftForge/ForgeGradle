package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.ECLIPSE_LOCATION;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;
import net.minecraftforge.gradle.tasks.user.reobf.ArtifactSpec;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;
import org.gradle.plugins.ide.idea.model.Dependency;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.gradle.plugins.ide.idea.model.Module;
import org.gradle.plugins.ide.idea.model.PathFactory;
import org.gradle.plugins.ide.idea.model.SingleEntryModuleLibrary;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class UserBasePlugin extends BasePlugin<UserExtension> implements IDelayedResolver<UserExtension>
{
    private boolean hasApplied = false;

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

        // lifecycle tasks
        Task task = makeTask("setupCIWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar");
        task.setGroup("ForgeGradle");
        
        task = makeTask("setupDevWorkspace", DefaultTask.class);
        task.dependsOn("genSrgs", "deobfBinJar", "copyAssets", "extractNatives");
        task.setGroup("ForgeGradle");
        
        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        task.dependsOn("setupDevWorkspace");
        task.setGroup("ForgeGradle");

        project.getTasks().getByName("eclipseClasspath").dependsOn("setupDevWorkspace");
    }

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
            task.dependsOn("extractUserDev", "downloadClient", "downloadServer");
        }

        GenSrgTask task2 = makeTask("genSrgs", GenSrgTask.class);
        {
            task2.setInSrg(delayedFile(UserConstants.PACKAGED_SRG));
            task2.setNotchToMcpSrg(delayedFile(UserConstants.DEOBF_MCP_SRG));
            task2.setMcpToSrgSrg(delayedFile(UserConstants.REOBF_SRG));
            task2.setMcpToNotchSrg(delayedFile(UserConstants.REOBF_NOTCH_SRG));
            task2.setMethodsCsv(delayedFile(UserConstants.METHOD_CSV));
            task2.setFieldsCsv(delayedFile(UserConstants.FIELD_CSV));
            task2.dependsOn("extractUserDev");
        }
        
        ApplyBinPatchesTask binTask = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
        {
            binTask.setInJar(delayedFile(Constants.JAR_MERGED));
            binTask.setOutJar(getBinPatchOut());
            binTask.setPatches(delayedFile(UserConstants.BINPATCHES));
            binTask.setClassesJar(delayedFile(UserConstants.BINARIES_JAR));
            binTask.setResources(delayedFileTree(UserConstants.RES_DIR));
            binTask.dependsOn("mergeJars");
        }
        
        ProcessJarTask deobfBinTask = makeTask("deobfBinJar", ProcessJarTask.class);
        {
            deobfBinTask.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            deobfBinTask.setSrg(delayedFile(UserConstants.DEOBF_MCP_SRG));
            deobfBinTask.setOutDirtyJar(delayedFile(Constants.DEOBF_BIN_JAR));
            addATs(deobfBinTask);
            deobfBinTask.setExceptorCfg(delayedFile(UserConstants.PACKAGED_EXC));
            deobfBinTask.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
            deobfBinTask.dependsOn(binTask);
        }
        
        
        ProcessJarTask deobfTask = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            deobfTask.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            deobfTask.setSrg(delayedFile(UserConstants.PACKAGED_SRG));
            deobfTask.setInJar(delayedFile(Constants.JAR_MERGED));
            deobfTask.setOutDirtyJar(delayedFile(Constants.DEOBF_JAR));
            addATs(deobfTask);
            deobfTask.setExceptorCfg(delayedFile(UserConstants.PACKAGED_EXC));
            deobfTask.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }
        
        // reobfuscate task.
        ReobfTask task4 = makeTask("reobf", ReobfTask.class);
        {
            task4.reobf(project.getTasks().getByName("jar"), new Action<ArtifactSpec>() {

                @Override
                public void execute(ArtifactSpec arg0)
                {
                    JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
                    arg0.setClasspath(javaConv.getSourceSets().getByName("main").getCompileClasspath());
                }
                
            });
            project.getTasks().getByName("assemble").dependsOn(task4);
        }

        Sync task5 = makeTask("copyAssets", Sync.class);
        {
            task5.from(delayedFile(Constants.ASSETS));
            task5.into(delayedFile("{ASSET_DIR}"));
            task5.dependsOn("getAssets");
        }
    }

    private void delayedTasks()
    {
        boolean clean = ((ProcessJarTask) project.getTasks().getByName("deobfuscateJar")).isClean();
        DelayedFile decompOut = clean ? getDecompOut() : delayedFile(Constants.DECOMP_JAR);
        
        DecompileTask decompile = makeTask("decompile", DecompileTask.class);
        {
            decompile.setInJar(((ProcessJarTask) project.getTasks().getByName("deobfBinJar")).getDelayedOutput());
            decompile.setOutJar(decompOut);
            decompile.setFernFlower(delayedFile(Constants.FERNFLOWER));
            decompile.setPatch(delayedFile(UserConstants.MCP_PATCH));
            decompile.setAstyleConfig(delayedFile(UserConstants.ASTYLE_CFG));
            decompile.dependsOn("downloadMcpTools", "deobfuscateJar", "genSrgs");
        }
        
        doPostDecompTasks(clean, "decompile");
    }
    
    protected abstract void doPostDecompTasks(boolean isClean, String decompTaskName);
    
    protected abstract DelayedFile getBinPatchOut();
    
    protected abstract DelayedFile getDecompOut();
    
    protected abstract void addATs(ProcessJarTask task);

    private void configureDeps()
    {
        // create configs
        project.getConfigurations().create(UserConstants.CONFIG_USERDEV);
        project.getConfigurations().create(UserConstants.CONFIG_API_JAVADOCS);
        project.getConfigurations().create(UserConstants.CONFIG_NATIVES);
        project.getConfigurations().create(UserConstants.CONFIG);

        // special userDev stuff
        ExtractTask extractUserDev = makeTask("extractUserDev", ExtractTask.class);
        extractUserDev.into(delayedFile(UserConstants.PACK_DIR));
        extractUserDev.doLast(new Action<Task>() {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES, arg0.getLogger());
            }
        });

        // special native stuff
        ExtractTask extractNatives = makeTask("extractNatives", ExtractTask.class);
        extractNatives.into(delayedFile(UserConstants.NATIVES_DIR));
        extractNatives.dependsOn("extractUserDev");
    }

    protected void configureCompilation()
    {
        Configuration config = project.getConfigurations().getByName(UserConstants.CONFIG);

        Javadoc javadoc = (Javadoc) project.getTasks().getByName("javadoc");
        javadoc.getClasspath().add(config);

        // get conventions
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");
        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");

        SourceSet main = javaConv.getSourceSets().getByName("main");
        SourceSet api = javaConv.getSourceSets().create("api");

        // set the Source
        javaConv.setSourceCompatibility("1.6");
        javaConv.setTargetCompatibility("1.6");

        // add to SourceSet compile paths
        api.setCompileClasspath(api.getCompileClasspath().plus(config));
        main.setCompileClasspath(main.getCompileClasspath().plus(config).plus(api.getOutput()));

        // add to eclipse and idea
        ideaConv.getModule().getScopes().get("COMPILE").get("plus").add(config);
        eclipseConv.getClasspath().getPlusConfigurations().add(config);

        // add sourceDirs to Intellij
        ideaConv.getModule().getSourceDirs().addAll(main.getAllSource().getFiles());
        ideaConv.getModule().getSourceDirs().addAll(api.getAllSource().getFiles());
    }

    @SuppressWarnings({"unchecked" })
    protected void configureEclipse()
    {
        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");

        eclipseConv.getClasspath().setDownloadJavadoc(true);
        eclipseConv.getClasspath().setDownloadSources(true);
        ((ActionBroadcast<Classpath>)eclipseConv.getClasspath().getFile().getWhenMerged()).add(new Action<Classpath>()
        {
            @Override
            public void execute(Classpath classpath)
            {
                String natives = delayedString(UserConstants.NATIVES_DIR).call().replace('\\', '/');
                for (ClasspathEntry e : classpath.getEntries())
                {
                    if (e instanceof Library)
                    {
                        Library lib = (Library)e;
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
                    map.put("value", delayedString(UserConstants.NATIVES_DIR).call());

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

    protected void configureIntellij()
    {
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        ideaConv.getModule().getExcludeDirs().addAll(project.files(".gradle", "build").getFiles());
        ideaConv.getModule().setDownloadJavadoc(true);
        ideaConv.getModule().setDownloadSources(true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        // grab the json && read dependencies
        if (delayedFile(UserConstants.JSON).call().exists())
        {
            readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES, project.getLogger());
        }

        // extract userdev
        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(UserConstants.CONFIG_USERDEV).getSingleFile().getAbsolutePath()));
        
        // add src ATs
        ProcessJarTask binDeobf = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        ProcessJarTask decompDeobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        
        // from the ExtensionObject
        binDeobf.addTransformer(getExtension().getAccessTransformers().toArray());
        
        // from the resources dirs
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            SourceSet main = javaConv.getSourceSets().getByName("main");
            SourceSet api = javaConv.getSourceSets().getByName("api");

            for (File at : main.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }

            for (File at : api.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }
        }
        
        
        final File deobfOut = ((ProcessJarTask) project.getTasks().getByName("deobfBinJar")).getOutJar();

        project.getDependencies().add(UserConstants.CONFIG, project.files(deobfOut));

        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");
        ((ActionBroadcast<Classpath>)eclipseConv.getClasspath().getFile().getWhenMerged()).add(new Action<Classpath>()
        {
            FileReferenceFactory factory = new FileReferenceFactory();
            @Override
            public void execute(Classpath classpath)
            {
                for (ClasspathEntry e : classpath.getEntries())
                {
                    if (e instanceof Library)
                    {
                        Library lib = (Library)e;
                        if (lib.getLibrary().getFile().equals(deobfOut))
                        {
                            lib.setJavadocPath(factory.fromFile(project.getConfigurations().getByName(UserConstants.CONFIG_API_JAVADOCS).getSingleFile()));
                            //TODO: Add the source attachment here....
                        }
                    }
                }
            }
        });

        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");
        ((ActionBroadcast<Module>) ideaConv.getModule().getIml().getWhenMerged()).add(new Action<Module>() {

            PathFactory factory = new PathFactory();
            @Override
            public void execute(Module module) {
                for (Dependency d : module.getDependencies()) {
                    if (d instanceof SingleEntryModuleLibrary) {
                        SingleEntryModuleLibrary lib = (SingleEntryModuleLibrary) d;
                        if (lib.getLibraryFile().equals(deobfOut))
                        {
                            lib.getJavadoc().add(factory.path("jar://" + project.getConfigurations().getByName(UserConstants.CONFIG_API_JAVADOCS).getSingleFile().getAbsolutePath().replace('\\', '/') + "!/"));
                            //TODO: Add the source attachment here....
                        }
                    }
                }
            }
        });

        fixEclipseProject(ECLIPSE_LOCATION);
        
        delayedTasks();
    }

    private static final byte[] LOCATION_BEFORE = new byte[]{ 0x40, (byte)0xB1, (byte)0x8B, (byte)0x81, 0x23, (byte)0xBC, 0x00, 0x14, 0x1A, 0x25, (byte)0x96, (byte)0xE7, (byte)0xA3, (byte)0x93, (byte)0xBE, 0x1E};
    private static final byte[] LOCATION_AFTER = new byte[]{ 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0xC0, 0x58, (byte)0xFB, (byte)0xF3, 0x23, (byte)0xBC, 0x00, 0x14, 0x1A, 0x51, (byte)0xF3, (byte)0x8C, 0x7B, (byte)0xBB, 0x77, (byte)0xC6};
    protected void fixEclipseProject(String path)
    {
        File f = new File(path);
        if (f.exists() && f.length() == 0)
        {
            String projectDir = "URI//file:/" + project.getProjectDir().getAbsolutePath().replace('\\', '/');
            try
            {
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(LOCATION_BEFORE); //Unknown but w/e
                fos.write((byte)((projectDir.length() & 0xFF) >> 8));
                fos.write((byte)((projectDir.length() & 0xFF) >> 0));
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
        if (hasApplied)
            return;

        ArrayList<String> libs = new ArrayList<String>();
        ArrayList<String> natives = new ArrayList<String>();

        try
        {
            Reader reader = Files.newReader(file, Charset.defaultCharset());
            JsonRootNode root = Constants.PARSER.parse(reader);
            
            log.lifecycle("READING JSON NOW");

            for (JsonNode node : root.getArrayNode("libraries"))
            {
                String dep = node.getStringValue("name");

                // its  maven central one
                if (dep.contains("_fixed"))
                {
                    // nope. we dont like fixed things.
                    continue;
                }
                else if (node.isNode("extract"))
                {
                    String osName = System.getProperty("os.name").toLowerCase();

                    if (osName.contains("linux") || osName.contains("unix"))
                        natives.add(dep + ":" + node.getStringValue("natives", "linux"));
                    else if (osName.contains("win"))
                        natives.add(dep + ":" + node.getStringValue("natives", "windows"));
                    else if (osName.contains("mac"))
                        natives.add(dep + ":" + node.getStringValue("natives", "osx"));
                    else
                    {
                        natives.add(dep + ":" + node.getStringValue("natives", "linux"));
                        natives.add(dep + ":" + node.getStringValue("natives", "windows"));
                        natives.add(dep + ":" + node.getStringValue("natives", "osx"));
                    }
                    natives.add(dep);
                }
                else
                {
                    libs.add(dep);
                }
            }
            
            reader.close();
            
            // apply the dep info.
            DependencyHandler handler = project.getDependencies();

            // actual dependencies
            if (project.getConfigurations().getByName(depConfig).getState() == State.UNRESOLVED)
                for (String dep : libs)
                    handler.add(depConfig, dep);
            else
                log.info("RESOLVED: "+depConfig);

            // the natives
            if (project.getConfigurations().getByName(nativeConfig).getState() == State.UNRESOLVED)
                for (String dep : natives)
                    handler.add(nativeConfig, dep);
            else
                log.info("RESOLVED: " + nativeConfig);

            hasApplied = true;
            
            // add stuff to the natives tas thing..
            // extract natives
            ExtractTask task = (ExtractTask) project.getTasks().findByName("extractNatives");
            for (File dep : project.getConfigurations().getByName(UserConstants.CONFIG_NATIVES).getFiles())
            {
                log.info("ADDING NATIVE: "+dep.getPath());
                task.from(delayedFile(dep.getAbsolutePath()));
            }
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }
    }

    @Override
    public String resolve(String pattern, Project project, UserExtension exten)
    {
        pattern = pattern.replace("{API_VERSION}", exten.getApiVersion());
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

package net.minecraftforge.gradle.user;

import groovy.lang.Closure;
import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.reobf.ArtifactSpec;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Configuration.State;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;

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
        task.setGroup("ForgeGradle");
        
        task = makeTask("setupDevWorkspace", DefaultTask.class);
        task.setGroup("ForgeGradle");
        task.dependsOn("genSrgs", "deobfuscateJar");
        
        task = makeTask("setupDecompWorkspace", DefaultTask.class);
        task.setGroup("ForgeGradle");

        (project.getTasksByName("eclipse", false).toArray(new Task[0])[0]).dependsOn("setupDevWorkspace");
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
            task2.setNotchToMcpSrg(delayedFile(UserConstants.DEOBF_SRG));
            task2.setMcpToSrgSrg(delayedFile(UserConstants.REOBF_SRG));
            task2.setMcpToNotchSrg(delayedFile(UserConstants.REOBF_NOTCH_SRG));
            task2.setMethodsCsv(delayedFile(UserConstants.METHOD_CSV));
            task2.setFieldsCsv(delayedFile(UserConstants.FIELD_CSV));
            task2.dependsOn("extractUserDev");
        }

        ProcessJarTask task3 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task3.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            task3.setSrg(delayedFile(UserConstants.DEOBF_SRG));
            addATs(task3);
            task3.setExceptorCfg(delayedFile(UserConstants.PACKAGED_EXC));
            task3.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
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
    }

    protected abstract void addATs(ProcessJarTask task);

    private void configureDeps()
    {
        // create configs
        project.getConfigurations().create(UserConstants.CONFIG_USERDEV);
        project.getConfigurations().create(UserConstants.CONFIG_NATIVES);
        project.getConfigurations().create(UserConstants.CONFIG);

        // special userDev stuff
        ExtractTask extractUserDev = makeTask("extractUserDev", ExtractTask.class);
        extractUserDev.into(delayedFile(UserConstants.PACK_DIR));
        extractUserDev.doLast(new Action<Task>() {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
            }
        });

        // special native stuff
        ExtractTask extractNatives = makeTask("extractNatives", ExtractTask.class);
        extractNatives.into(delayedFile(UserConstants.NATIVES_DIR));
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

    @SuppressWarnings("serial")
    protected void configureEclipse()
    {
        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");

        eclipseConv.getClasspath().setDownloadJavadoc(true);
        eclipseConv.getClasspath().setDownloadSources(true);

        // XML NATIVESHACKERY
        eclipseConv.getClasspath().getFile().withXml(new Closure<Object>(project) {
            @SuppressWarnings("unchecked")
            public Object call(Object... obj)
            {
                Node root = ((XmlProvider) getDelegate()).asNode();

                HashMap<String, String> map = new HashMap<String, String>();
                map.put("name", "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY");
                map.put("value", delayedString(UserConstants.NATIVES_DIR).call());

                for (Node child : (List<Node>) root.children())
                {
                    String path = (String) child.attribute("path");

                    if (path == null)
                        continue;
                    else if (!path.contains("lwjg") && !path.contains("jinput"))
                        continue;

                    if (child.children().isEmpty())
                        child.appendNode("attributes").appendNode("attribute", map);
                    else
                    {
                        for (Node attrib : (List<Node>) child.children())
                        {
                            if (attrib.name().toString().equals("attributes"))
                            {
                                attrib.appendNode("attribute", map);
                                break;
                            }
                        }
                    }
                }

                return null;
            }
        });

        Task task = makeTask("afterEclipseImport", DefaultTask.class);
        task.doLast(new Action<Object>() {
            @SuppressWarnings("unchecked")
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
                    // TODO Auto-generated catch block
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

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();

        if (delayedFile(UserConstants.JSON).call().exists())
        {
            readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
        }

        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(UserConstants.CONFIG_USERDEV).getSingleFile().getAbsolutePath()));

        ExtractTask natives = (ExtractTask) project.getTasks().findByName("extractNatives");
        for (File file : project.getConfigurations().getByName(UserConstants.CONFIG_NATIVES).getFiles())
            natives.from(delayedFile(file.getAbsolutePath()));
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

    private void readAndApplyJson(File file, String depConfig, String nativeConfig)
    {
        if (hasApplied)
            return;
        
        if (project.getConfigurations().getByName(depConfig).getState() != State.UNRESOLVED)
            return;
        else if (project.getConfigurations().getByName(nativeConfig).getState() != State.UNRESOLVED)
            return;
            

        ArrayList<String> libs = new ArrayList<String>();
        ArrayList<String> natives = new ArrayList<String>();

        try
        {
            JsonRootNode root = Constants.PARSER.parse(Files.newReader(file, Charset.defaultCharset()));

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
                    natives.add(dep);
                }
                else
                {
                    libs.add(dep);
                }
            }
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }

        // apply the dep info.
        DependencyHandler handler = project.getDependencies();

        for (String dep : libs)
            handler.add(depConfig, dep);

        for (String dep : natives)
            handler.add(nativeConfig, dep);
        
        hasApplied = true;
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

package net.minecraftforge.gradle.user.lib;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.reobf.ArtifactSpec;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Joiner;

public abstract class UserLibBasePlugin extends UserBasePlugin<UserExtension>
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);
        
        // for special packaging.
        // make jar end with .litemod for litemod, and who knows what else for other things.
        ((Jar) project.getTasks().getByName("jar")).setExtension(getJarExtension());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void applyOverlayPlugin()
    {
        // add in extension
        project.getExtensions().create(actualApiName(), getExtensionClass(), this);
        
        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);

        // override run configs if needed
        if (shouldOverrideRunConfigs())
        {
            overrideRunConfigs();
        }

        configurePackaging();

        // ensure we get basic things from the other extension
        project.afterEvaluate(new Action() {

            @Override
            public void execute(Object arg0)
            {
                getOverlayExtension().copyFrom(otherPlugin.getExtension());
            }

        });
    }

    @SuppressWarnings("rawtypes")
    protected void configurePackaging()
    {
        String cappedApiName = Character.toUpperCase(actualApiName().charAt(0)) + actualApiName().substring(1);
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        // create apiJar task
        Jar jarTask = makeTask("jar" + cappedApiName, Jar.class);
        jarTask.from(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());
        jarTask.setClassifier(actualApiName());
        jarTask.setExtension(getJarExtension());

        // configure otherPlugin task to have a classifier
        ((Jar) project.getTasks().getByName("jar")).setClassifier(((UserBasePlugin) otherPlugin).getApiName());

        //  configure reobf for litemod
        ((ReobfTask) project.getTasks().getByName("reobf")).reobf(jarTask, new Action<ArtifactSpec>()
        {
            @Override
            public void execute(ArtifactSpec spec)
            {
                spec.setSrgMcp();

                JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
                spec.setClasspath(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getCompileClasspath());
            }

        });

        project.getArtifacts().add("archives", jarTask);
    }

    @Override
    public final boolean canOverlayPlugin()
    {
        return true;
    }

    public abstract boolean shouldOverrideRunConfigs();

    abstract String actualApiName();

    @SuppressWarnings("serial")
    private void overrideRunConfigs()
    {
        // run tasks

        JavaExec exec = (JavaExec) project.getTasks().getByName("runClient");
        {
            exec.setMain(getClientRunClass());
            exec.setArgs(getClientRunArgs());
        }

        exec = (JavaExec) project.getTasks().getByName("runServer");
        {
            exec.setMain(getServerRunClass());
            exec.setArgs(getServerRunArgs());
        }

        exec = (JavaExec) project.getTasks().getByName("debugClient");
        {
            exec.setMain(getClientRunClass());
            exec.setArgs(getClientRunArgs());
        }

        exec = (JavaExec) project.getTasks().getByName("debugServer");
        {
            exec.setMain(getServerRunClass());
            exec.setArgs(getServerRunArgs());
        }

        // idea run configs

        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        Task task = project.getTasks().getByName("genIntellijRuns");
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

                    overrideIntellijRuns(doc, module);

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
                    overrideIntellijRuns(doc, project.getProjectDir().getCanonicalPath());
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                return null;
            }
        });
    }

    private final void overrideIntellijRuns(Document doc, String module) throws DOMException, IOException
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

        NodeList list = root.getElementsByTagName("configuration");
        for (int i = 0; i < list.getLength(); i++)
        {
            Element e = (Element) list.item(i);

            String runClass, args;
            if ("Minecraft Client".equals(e.getAttribute("name")))
            {
                runClass = getClientRunClass();
                args = Joiner.on(' ').join(getClientRunArgs());
            }
            else if ("Minecraft Server".equals(e.getAttribute("name")))
            {
                runClass = getServerRunClass();
                args = Joiner.on(' ').join(getServerRunArgs());
            }
            else
            {
                continue;
            }

            NodeList list2 = e.getElementsByTagName("option");
            for (int j = 0; j < list2.getLength(); j++)
            {
                Element e2 = (Element) list2.item(j);
                if ("MAIN_CLASS_NAME".equals(e2.getAttribute("name")))
                {
                    e2.setAttribute("value", runClass);
                }
                else if ("PROGRAM_PARAMETERS".equals(e2.getAttribute("name")))
                {
                    e2.setAttribute("value", args);
                }
            }

        }
    }

    @Override
    public String getApiName()
    {
        return "minecraft_merged";
    }

    @Override
    protected String getSrcDepName()
    {
        return "minecraft_merged_src";
    }

    @Override
    protected String getBinDepName()
    {
        return "minecraft_merged_bin";
    }

    @Override
    protected boolean hasApiVersion()
    {
        return false;
    }

    @Override
    protected String getApiVersion(UserExtension exten)
    {
        // unnecessary.
        return null;
    }

    @Override
    protected String getMcVersion(UserExtension exten)
    {
        return exten.getVersion();
    }

    @Override
    protected String getApiCacheDir(UserExtension exten)
    {
        return "{CACHE_DIR}/minecraft/net/minecraft/minecraft_merged/{MC_VERSION}";
    }

    @Override
    protected DelayedFile getDevJson()
    {
        return delayedFile(getFmlCacheDir() + "/unpacked/dev.json");
    }

    @Override
    protected String getSrgCacheDir(UserExtension exten)
    {
        return getFmlCacheDir() + "/srgs";
    }

    @Override
    protected String getUserDevCacheDir(UserExtension exten)
    {
        return getFmlCacheDir() + "/unpacked";
    }

    private final String getFmlCacheDir()
    {
        return "{CACHE_DIR}/minecraft/cpw/mods/fml/" + getFmlVersion();
    }

    private final String getFmlVersion()
    {
        return "1.7.2-7.2.158.889";
    }

    @Override
    protected String getUserDev()
    {
        // hardcoded version of FML... for now..
        return "cpw.mods:fml:" + getFmlVersion();
    }

    @Override
    protected final void configureDeobfuscation(ProcessJarTask task)
    {
        // no access transformers...
    }
    
    protected String getJarExtension()
    {
        return "jar";
    }

    @Override
    protected final void doVersionChecks(String version)
    {
        if (!"1.7.2".equals(version))
            throw new RuntimeException("ForgeGradle 1.2 does not support " + version);
    }

    public UserExtension getOverlayExtension()
    {
        return (UserExtension) project.getExtensions().getByName(actualApiName());
    }
}

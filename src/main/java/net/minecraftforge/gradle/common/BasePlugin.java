package net.minecraftforge.gradle.common;

import static net.minecraftforge.gradle.common.Constants.ECLIPSE_NATIVES;
import static net.minecraftforge.gradle.common.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_JENKINS;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;
import static net.minecraftforge.gradle.common.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.common.Constants.MCP_URL;
import static net.minecraftforge.gradle.common.Constants.MC_JAR_URL;
import static net.minecraftforge.gradle.common.Constants.MC_SERVER_URL;
import static net.minecraftforge.gradle.common.Constants.OPERATING_SYSTEM;
import groovy.lang.Closure;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.ObtainMcpStuffTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.testfixtures.ProjectBuilder;

import argo.jdom.JsonNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>
{
    protected Project project;

    @Override
    @SuppressWarnings("serial")
    public final void apply(Project arg)
    {
        project = arg;
        
        project.getExtensions().create(EXT_NAME_MC, getExtensionClass(), project);
        project.getExtensions().create(EXT_NAME_JENKINS, JenkinsExtension.class, project);
        
        arg.afterEvaluate(new Closure<Object>(this){
            @Override
            public Object call()
            {   
                nativeTasks();
                afterEvaluate();
                return null;
            }
        });
        
        // download tasks
        DownloadTask task;

        task = makeTask("downloadClient", DownloadTask.class);
        {
            task.setOutput(delayedFile(JAR_CLIENT_FRESH));
            task.setUrl(delayedString(MC_JAR_URL));
        }

        task = makeTask("downloadServer", DownloadTask.class);
        {
            task.setOutput(delayedFile(JAR_SERVER_FRESH));
            task.setUrl(delayedString(MC_SERVER_URL));
        }
        
        ObtainMcpStuffTask mcpTask = makeTask("downloadMcpTools", ObtainMcpStuffTask.class);
        {
            mcpTask.setMcpUrl(delayedString(MCP_URL));
            mcpTask.setFfJar(delayedFile(FERNFLOWER));
            mcpTask.setInjectorJar(delayedFile(EXCEPTOR));
        }
    }
    
    public abstract void applyPlugin();
    
    protected abstract String getDevJson();
    
    public void afterEvaluate() {}
    
    public final void nativeTasks()
    {
        try
        {
            Copy copyTask = makeTask("extractNatives", Copy.class);
            {
                copyTask.exclude("META-INF", "META-INF/**", "META-INF/*");
                copyTask.into(delayedString(ECLIPSE_NATIVES).call());
                copyTask.dependsOn("extractWorkspace");
            }

            String devJson = getDevJson();
            if (devJson == null)
            {
                project.getLogger().info("Dev json not set, could not create native downloads tasks");
                return;
            }
            
            JsonNode node = null;
            File jsonFile = delayedFile(devJson).call().getAbsoluteFile(); // ToDo: Support files in zips, for Modder dev workspace.
            node = Constants.PARSER.parse(Files.newReader(jsonFile, Charset.defaultCharset()));

            for (JsonNode lib : node.getArrayNode("libraries"))
            {
                if (lib.isNode("natives") && lib.isNode("extract"))
                {
                    String notation = lib.getStringValue("name");
                    String[] s = notation.split(":");
                    String path = String.format("%s/%s/%s/%s-%s-natives-%s.jar",
                            s[0].replace('.', '/'), s[1], s[2], s[1], s[2], OPERATING_SYSTEM
                    );

                    
                    DownloadTask task = makeTask("downloadNatives-" + s[1], DownloadTask.class);
                    {
                        task.setOutput(delayedFile("{CACHE_DIR}/" + path));
                        task.setUrl(delayedString("http://repo1.maven.org/maven2/" + path));
                    }

                    copyTask.from(delayedZipTree("{CACHE_DIR}/" + path));
                    copyTask.dependsOn("downloadNatives-" + s[1]);
                }
            }

        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }
    }
    
    /**
     * This extension object will have the name "minecraft"
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Class<K> getExtensionClass(){ return (Class<K>) BaseExtension.class; }
    
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
        Project project = ProjectBuilder.builder()
                .withProjectDir(buildFile.getParentFile())
                .withName(buildFile.getParentFile().getName())
                .withParent(parent)
                .build();

        HashMap<String, String> map = new HashMap<String, String>();
        map.put("from", buildFile.getName());

        project.apply(map);

        return project;
    }

    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true); }

}

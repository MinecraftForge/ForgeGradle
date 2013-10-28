package net.minecraftforge.gradle;

import static net.minecraftforge.gradle.Constants.*;

import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

import groovy.lang.Closure;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedString.IDelayedResolver;
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

public class BasePlugin implements Plugin<Project>, IDelayedResolver
{
    public Project project;

    @Override
    public void apply(Project project)
    {
        this.project = project;
        project.getExtensions().create(EXT_NAME_MC, getExtension(), project);
        project.getExtensions().create(EXT_NAME_JENKINS, JenkinsExtensionObject.class, project);

        createObtainingTasks();
    }
    
    protected Class<? extends ExtensionObject> getExtension(){ return ExtensionObject.class; }
    protected String getDevJson(){ return null; }

    @Override
    public String resolve(String patern, Project project, ExtensionObject extension)
    {
        return patern;
    }

    @SuppressWarnings({ "serial", "rawtypes" })
    protected void createObtainingTasks()
    {
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

        project.afterEvaluate(new Closure(project, this)
        {
            @Override
            public Object call()
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
                        return null;
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
                
                return null;
            }
            @Override public Object call(Object... obj){ return call(); }
        });
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

    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path, this); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path, this); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path, this); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true, this); }
}

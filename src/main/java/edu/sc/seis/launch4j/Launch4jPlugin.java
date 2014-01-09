package edu.sc.seis.launch4j;

import java.io.File;
import java.util.HashMap;

import groovy.lang.Closure;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.bundling.Jar;

public class Launch4jPlugin implements Plugin<Project> 
{

    static final String LAUNCH4J_PLUGIN_NAME = "launch4j";
    static final String LAUNCH4J_GROUP = LAUNCH4J_PLUGIN_NAME;

    static final String LAUNCH4J_CONFIGURATION_NAME = "launch4j";
    static final String TASK_XML_GENERATE_NAME = "generateXmlConfig";
    static final String TASK_LIB_COPY_NAME = "copyL4jLib";
    static final String TASK_RUN_NAME = "createExe";
    static final String TASK_LAUNCH4J_NAME = "launch4j";
    
    Project project;

    public void apply(Project project)
    {
        this.project = project;
        
        project.getConfigurations().create(LAUNCH4J_CONFIGURATION_NAME)
            .setVisible(false)
            .setTransitive(true)
            .setDescription("The launch4j configuration for this project.");
        
        Launch4jPluginExtension pluginExtension = new Launch4jPluginExtension();
        project.getExtensions().add("launch4j", pluginExtension);
        
        Task xmlTask = addCreateLaunch4jXMLTask(project, pluginExtension);
        
        Task copyTask = addCopyToLibTask(project, pluginExtension);
        
        Task runTask = addRunLauch4jTask(project, pluginExtension);
        runTask.dependsOn(copyTask);
        runTask.dependsOn(xmlTask);
        
        Task l4jTask = addLaunch4jTask(project, pluginExtension);
        l4jTask.dependsOn(runTask);

        Launch4jPluginExtension ext = (Launch4jPluginExtension)project.getExtensions().getByName("launch4j");
        ext.initExtensionDefaults(project);
    }

    private Task addCreateLaunch4jXMLTask(Project project, Launch4jPluginExtension configuration)
    {
        CreateLaunch4jXMLTask task = makeTask(TASK_XML_GENERATE_NAME, CreateLaunch4jXMLTask.class);
        task.setDescription("Creates XML configuration file used by launch4j to create an windows exe.");
        task.setGroup(LAUNCH4J_GROUP);
        task.getInputs().property("project version", project.getVersion());
        task.getInputs().property("Launch4j extension", configuration);
        task.getOutputs().file(project.file(configuration.getXmlFileName()));
        return task;
    }

    @SuppressWarnings("serial")
    private Task addCopyToLibTask(Project project, Launch4jPluginExtension configuration)
    {
        final Sync task = makeTask(TASK_LIB_COPY_NAME, Sync.class);
        task.setDescription("Copies the project dependency jars in the lib directory.");
        task.setGroup(LAUNCH4J_GROUP);
        // more stuff with the java plugin
        //task.with(configureDistSpec(project));
        task.into( new Closure<File>(null)
        {
            @Override
            public File call(Object... obj)
            {
                Launch4jPluginExtension ext = ((Launch4jPluginExtension) task.getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME));
                return task.getProject().file(task.getProject().getBuildDir() + "/" + ext.getOutputDir() + "/lib");
            }
        });
        return task;
    }

    private Task addRunLauch4jTask(Project project, Launch4jPluginExtension configuration)
    {
        final Exec task = makeTask(TASK_RUN_NAME, Exec.class);
        task.setDescription("Runs launch4j to generate an .exe file");
        task.setGroup(LAUNCH4J_GROUP);
        // TODO
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                Launch4jPluginExtension ext = ((Launch4jPluginExtension) task.getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME));
                
                task.setCommandLine(ext.getLaunch4jCmd(), project.getBuildDir() + "/" + ext.getOutputDir() + "/" + ext.getXmlFileName());
                task.setWorkingDir(project.file(ext.getChdir()));
            }
        });
        return task;
    }
    
    private Task addLaunch4jTask(Project project, Launch4jPluginExtension configuration)
    {
        DefaultTask task = makeTask(TASK_LAUNCH4J_NAME);
        task.setDescription("Placeholder task for tasks relating to creating .exe applications with launch4j");
        task.setGroup(LAUNCH4J_GROUP);
        return task;
    }

    @SuppressWarnings({ "serial", "unused" })
    private CopySpec configureDistSpec(Project project)
    {
        CopySpec distSpec = project.copySpec(new Closure<Object>(null) {});
        Jar jar = (Jar) project.getTasks().getByName(JavaPlugin.JAR_TASK_NAME);
        
        distSpec.from(jar);
        distSpec.from(project.getConfigurations().getByName("runtime"));

        return distSpec;
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
}






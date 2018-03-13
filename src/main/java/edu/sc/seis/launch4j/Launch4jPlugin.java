package edu.sc.seis.launch4j;

import groovy.lang.Closure;

import java.io.File;
import java.util.HashMap;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.EtagDownloadTask;
import net.minecraftforge.gradle.tasks.ExtractTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Jar;

public class Launch4jPlugin implements Plugin<Project>
{

    static final String LAUNCH4J_PLUGIN_NAME        = "launch4j";
    static final String LAUNCH4J_GROUP              = LAUNCH4J_PLUGIN_NAME;
    static final String LAUNCH4J_CONFIGURATION_NAME = LAUNCH4J_PLUGIN_NAME;

    static final String TASK_XML_GENERATE_NAME      = "generateXmlConfig";
    static final String TASK_LIB_COPY_NAME          = "copyL4jLib";
    static final String TASK_DL                     = "downloadLaunch4J";
    static final String TASK_EXTRACT                = "extractLaunch4J";
    static final String TASK_RUN_NAME               = "createExe";
    static final String TASK_LAUNCH4J_NAME          = "launch4j";

    static final String URL_LAUNCH4J                = "http://files.minecraftforge.net/launch4j/launch4j-3.8.0-" + Constants.OPERATING_SYSTEM + ".zip";

    static final String ZIP_LAUNCH4J                = "build/launch4j.zip";
    static final String DIR_LAUNCH4J                = "build/launch4j";

    private Project     project;

    public void apply(Project project)
    {
        this.project = project;

        project.getConfigurations().create(LAUNCH4J_CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription("The launch4j configuration for this project.");

        project.getExtensions().add(LAUNCH4J_PLUGIN_NAME, Launch4jPluginExtension.class);

        Launch4jPluginExtension ext = (Launch4jPluginExtension) project.getExtensions().getByName(LAUNCH4J_PLUGIN_NAME);
        ext.initExtensionDefaults(project);

        File downloadedLaunch4J = project.file(ZIP_LAUNCH4J);
        File launch4JDir = project.file(DIR_LAUNCH4J);

        Task dlTask = addDownloadTask(URL_LAUNCH4J, downloadedLaunch4J);

        Task extractTask = addExtractTask(downloadedLaunch4J, launch4JDir);
        extractTask.dependsOn(dlTask);

        Task xmlTask = addCreateLaunch4jXMLTask(ext);

        Task copyTask = addCopyToLibTask();

        Task runTask = addRunLauch4jTask(launch4JDir);
        runTask.dependsOn(extractTask, copyTask, xmlTask);

        Task l4jTask = addLaunch4jTask();
        l4jTask.dependsOn(runTask);
    }

    private Task addDownloadTask(String url, File output)
    {
        EtagDownloadTask dlTask = makeTask(TASK_DL, EtagDownloadTask.class);
        dlTask.setFile(output);
        dlTask.setUrl(url);
        return dlTask;
    }

    private Task addExtractTask(final File input, final File output)
    {
        ExtractTask extractTask = makeTask(TASK_EXTRACT, ExtractTask.class);
        extractTask.from(input);
        extractTask.into(output);

        extractTask.doLast(new Action<Task>() {

            @Override
            public void execute(Task task)
            {
                FileTree tree = project.fileTree(output.getPath() + "/bin");
                tree.visit(new FileVisitor()
                {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails)
                    {
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails)
                    {
                        if (!fileDetails.getFile().canExecute())
                        {
                            boolean worked = fileDetails.getFile().setExecutable(true);
                            project.getLogger().info("Setting file +X " + worked + " : " + fileDetails.getPath());
                        }
                    }
                });
            }
        });

        return extractTask;
    }

    private Task addCreateLaunch4jXMLTask(Launch4jPluginExtension configuration)
    {
        CreateLaunch4jXMLTask task = makeTask(TASK_XML_GENERATE_NAME, CreateLaunch4jXMLTask.class);
        task.setDescription("Creates XML configuration file used by launch4j to create an windows exe.");
        task.setGroup(LAUNCH4J_GROUP);
        task.getOutputs().upToDateWhen(Constants.CALL_FALSE);
        return task;
    }

    @SuppressWarnings("serial")
    private Task addCopyToLibTask()
    {
        final Sync task = makeTask(TASK_LIB_COPY_NAME, Sync.class);
        task.setDescription("Copies the project dependency jars in the lib directory.");
        task.setGroup(LAUNCH4J_GROUP);
        // more stuff with the java plugin
        //task.with(configureDistSpec(project));
        task.into(new Closure<File>(Launch4jPlugin.class)
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

    private Task addRunLauch4jTask(final File launch4JDir)
    {
        final JavaExec task = makeTask(TASK_RUN_NAME, JavaExec.class);
        task.setDescription("Runs launch4j to generate an .exe file");
        task.setGroup(LAUNCH4J_GROUP);
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                Launch4jPluginExtension ext = ((Launch4jPluginExtension) task.getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME));

                task.setMain("net.sf.launch4j.Main");
                task.args(project.getBuildDir() + "/" + ext.getOutputDir() + "/" + ext.getXmlFileName());
                task.setWorkingDir(project.file(ext.getChdir()));
                task.setClasspath(project.fileTree(launch4JDir));
            }
        });
        return task;
    }

    private Task addLaunch4jTask()
    {
        DefaultTask task = makeTask(TASK_LAUNCH4J_NAME);
        task.setDescription("Placeholder task for tasks relating to creating .exe applications with launch4j");
        task.setGroup(LAUNCH4J_GROUP);
        return task;
    }

    @SuppressWarnings({ "serial", "unused" })
    private static CopySpec configureDistSpec(Project project)
    {
        CopySpec distSpec = project.copySpec(new Closure<Object>(Launch4jPlugin.class) {});
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

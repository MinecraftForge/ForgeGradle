package net.minecraftforge.gradle.user;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.GenSrgTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;

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

        configureDeps();
        configureCompilation();

        tasks();

        // lifecycle tasks
        makeTask("setupCIWorkspace", DefaultTask.class);
        makeTask("setupDevWorkspace", DefaultTask.class);
        makeTask("setupDecompWorkspace", DefaultTask.class);
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
            task.dependsOn("downloadClient", "downloadServer", "extractUserDev");
        }

        GenSrgTask task2 = makeTask("genSrgs", GenSrgTask.class);
        {
            task2.setInSrg(delayedFile(UserConstants.PACKAGED_SRG));
            task2.setDeobfSrg(delayedFile(UserConstants.DEOBF_SRG));
            task2.setReobfSrg(delayedFile(UserConstants.REOBF_SRG));
            task2.setMethodsCsv(delayedFile(UserConstants.METHOD_CSV));
            task2.setFieldsCsv(delayedFile(UserConstants.FIELD_CSV));
            task2.dependsOn("extractUserDev");
        }

        ProcessJarTask task3 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task3.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            task3.setSrg(delayedFile(UserConstants.PACKAGED_SRG));
            addATs(task3);
            task3.setExceptorCfg(delayedFile(UserConstants.PACKAGED_EXC));
            task3.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
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
        final ExtractTask extracter = makeTask("extractUserDev", ExtractTask.class);
        extracter.into(delayedFile(UserConstants.PACK_DIR));
        extracter.doLast(new Action<Task>() {
            @Override
            public void execute(Task arg0)
            {
                readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
            }
        });
    }
    
    private void configureCompilation()
    {
        Configuration config = project.getConfigurations().getByName(UserConstants.CONFIG);
        
        Javadoc javadoc = (Javadoc) project.getTasks().getByName("javadoc");
        javadoc.getClasspath().add(config);
        
        JavaPluginConvention conv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        
        SourceSet api = conv.getSourceSets().getByName("main");
        SourceSet main = conv.getSourceSets().create("api");
        
        api.setCompileClasspath(api.getCompileClasspath().plus(config));
        main.setCompileClasspath(main.getCompileClasspath().plus(config).plus(api.getOutput()));
    }

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();

        if (delayedFile(UserConstants.JSON).call().exists())
        {
            readAndApplyJson(delayedFile(UserConstants.JSON).call(), UserConstants.CONFIG, UserConstants.CONFIG_NATIVES);
        }

        project.getDependencies().add(UserConstants.CONFIG_USERDEV, getExtension().getNotation() + ":userdev");
        ((ExtractTask) project.getTasks().findByName("extractUserDev")).from(delayedFile(project.getConfigurations().getByName(UserConstants.CONFIG_USERDEV).getSingleFile().getAbsolutePath()));

        FileCollection files = project.files(delayedString(UserConstants.JAVADOC_JAR).call(), delayedString(UserConstants.ASTYLE_CFG).call());
        project.getDependencies().add(UserConstants.CONFIG, files);
    }

    private void readAndApplyJson(File file, String depConfig, String nativeConfig)
    {
        if (hasApplied)
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

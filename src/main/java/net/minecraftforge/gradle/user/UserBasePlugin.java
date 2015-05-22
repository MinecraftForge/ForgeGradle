package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.MavenPluginConvention;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.scala.ScalaCompile;

public abstract class UserBasePlugin<T extends UserExtension> extends BasePlugin<T>
{
    @Override
    public final void applyPlugin()
    {
        // apply the plugins
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("eclipse");
        this.applyExternalPlugin("idea");

        // life cycle tasks
        Task task = makeTask(TASK_SETUP_CI, DefaultTask.class);
        task.setDescription("Sets up the bare minimum to build a minecraft mod. Ideally for CI servers");
        task.setGroup("ForgeGradle");

        task = makeTask(TASK_SETUP_DEV, DefaultTask.class);
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");

        task = makeTask(TASK_SETUP_DECOMP, DefaultTask.class);
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");

        // create configs
        project.getConfigurations().maybeCreate(CONFIG_MC);
        project.getConfigurations().maybeCreate(CONFIG_PROVIDED);
        project.getConfigurations().maybeCreate(CONFIG_START);

        configureCompilation();

        // Quality of life stuff for the users
        createSourceCopyTasks();

        // use zinc for scala compilation
        project.getTasks().withType(ScalaCompile.class, new Action<ScalaCompile>() {
            @Override
            public void execute(ScalaCompile t)
            {
                t.getScalaCompileOptions().setUseAnt(false);
            }
        });

        applyUserPlugin();
    }

    @Override
    protected void afterEvaluate()
    {
        super.afterEvaluate();

        // map configurations (only if the maven or maven publish plugins exist)
        mapConfigurations();

        // configure source replacement.
        project.getTasks().withType(TaskSourceCopy.class, new Action<TaskSourceCopy>() {
            @Override
            public void execute(TaskSourceCopy t)
            {
                t.replace(getExtension().getReplacements());
                t.include(getExtension().getIncludes());
            }
        });

        // add access transformers to deobf tasks
        addAtsToDeobf();
    }

    protected abstract void applyUserPlugin();

    protected void tasksClient(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_CLIENT_FRESH), delayedFile(MCP_PATCHES_CLIENT));
    }

    protected void tasksServer(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_SERVER_FRESH), delayedFile(MCP_PATCHES_MERGED));
    }

    protected void tasksMerged(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_MERGED), delayedFile(MCP_PATCHES_MERGED));
    }

    private void makeDecompTasks(String globalOutputPattern, String localOutputPattern, String taskSuffix, Object inputJar, Object mcpPatchSet)
    {
        DeobfuscateJar deobfBin = makeTask(TASK_DEOBF_BIN + taskSuffix, DeobfuscateJar.class);
        {
            deobfBin.setSrg(delayedFile(SRG_NOTCH_TO_MCP));
            deobfBin.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfBin.setExceptorCfg(delayedFile(EXC_MCP));
            deobfBin.setApplyMarkers(false);
            deobfBin.setInJar(inputJar);
            deobfBin.setOutJar(chooseDeobfOutput(globalOutputPattern, localOutputPattern, "Bin"));
            deobfBin.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }

        Object deobfDecompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-deobfDecomp");
        Object decompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-decomp");
        Object postDecompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-sources");
        Object recompiledJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "");

        DeobfuscateJar deobfDecomp = makeTask(TASK_DEOBF + taskSuffix, DeobfuscateJar.class);
        {
            deobfDecomp.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfDecomp.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfDecomp.setExceptorCfg(delayedFile(EXC_SRG));
            deobfDecomp.setApplyMarkers(false);
            deobfDecomp.setInJar(inputJar);
            deobfBin.setOutJar(deobfDecompJar);
            deobfDecomp.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }

        ApplyFernFlowerTask decompile = makeTask(TASK_DECOMPILE + taskSuffix, ApplyFernFlowerTask.class);
        {
            decompile.setInJar(deobfDecompJar);
            decompile.setOutJar(decompJar);
            decompile.setFernflower(delayedFile(JAR_FERNFLOWER));
            decompile.dependsOn(TASK_DL_FERNFLOWER, deobfDecomp);
        }

        PostDecompileTask postDecomp = makeTask(TASK_POST_DECOMP + taskSuffix, PostDecompileTask.class);
        {
            postDecomp.setInJar(decompJar);
            postDecomp.setOutJar(postDecompJar);
            postDecomp.setPatches(delayedFile(MCP_PATCHES_MERGED));
            postDecomp.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecomp.dependsOn(decompile);
        }

        TaskRecompileMc recompile = makeTask(TASK_RECOMPILE + taskSuffix, TaskRecompileMc.class);
        {
            recompile.setInSources(postDecompJar);
            recompile.setClasspath(CONFIG_MC_DEPS);
            recompile.setOutJar(recompiledJar);
            recompile.dependsOn(postDecomp);
        }

        // add setup dependencies
        project.getTasks().getByName(TASK_SETUP_CI).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DEV).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DECOMP).dependsOn(recompile);
    }

    /**
     * This method returns a closure that
     * @return
     */
    @SuppressWarnings("serial")
    private Object chooseDeobfOutput(final String globalPattern, final String localPattern, final String classifier)
    {
        return new Closure<DelayedFile>(project, this) {
            public DelayedFile call()
            {
                String str = useLocalCache(getExtension()) ? localPattern : globalPattern;
                return delayedFile(String.format(str, classifier));
            }
        };
    }

    /**
     * A boolean used to cache the output of useLocalCache;
     * @see useLocalCache
     */
    protected boolean useLocalCache = false;

    /**
     * This method is called sufficiently late. Either afterEvaluate or inside a task, thus it has the extension object.
     * This method is called to decide whether or not to use the project-local cache instead of the global cache.
     * The actual locations of each cache are specified elsewhere.
     * TODO: add see annotations
     * @param extension The extension object of this plugin
     * @return whether or not to use the local cache
     */
    protected boolean useLocalCache(T extension)
    {
        if (useLocalCache)
            return true;

        // checks to see if any access transformers were added.
        useLocalCache = !extension.getAccessTransformers().isEmpty();

        return useLocalCache;
    }

    /**
     * Creates the api SourceSet and configures the classpaths of all the SourceSets to have MC and the MC deps in them.
     * Also sets the target JDK to java 6
     */
    protected void configureCompilation()
    {
        // get convention
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = javaConv.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        SourceSet api = javaConv.getSourceSets().create("api");

        api.setCompileClasspath(api.getCompileClasspath()
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_START))
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));
        main.setCompileClasspath(main.getCompileClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_START))
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));
        test.setCompileClasspath(test.getCompileClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_START))
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));

        project.getConfigurations().getByName("apiCompile").extendsFrom(project.getConfigurations().getByName("compile"));
        project.getConfigurations().getByName("testCompile").extendsFrom(project.getConfigurations().getByName("apiCompile"));

        // set the compile target
        javaConv.setSourceCompatibility("1.6");
        javaConv.setTargetCompatibility("1.6");
    }

    /**
     * Creates and partially configures the source replacement tasks. The actual replacements must be configured afterEvaluate.
     */
    protected void createSourceCopyTasks()
    {
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        // do the special source moving...
        TaskSourceCopy task;

        // main
        {
            File dir = new File(project.getBuildDir(), SourceSet.MAIN_SOURCE_SET_NAME + "/java");

            task = makeTask("sourceMainJava", TaskSourceCopy.class);
            task.setSource(main.getJava());
            task.setOutput(dir);

            // must get replacements from extension afterEValuate()

            JavaCompile compile = (JavaCompile) project.getTasks().getByName(main.getCompileJavaTaskName());
            compile.dependsOn("sourceMainJava");
            compile.setSource(dir);
        }

        // scala!!!
        if (project.getPlugins().hasPlugin("scala"))
        {
            ScalaSourceSet set = (ScalaSourceSet) new DslObject(main).getConvention().getPlugins().get("scala");
            File dir = new File(project.getBuildDir(), SourceSet.MAIN_SOURCE_SET_NAME + "/scala");

            task = makeTask("sourceMainScala", TaskSourceCopy.class);
            task.setSource(set.getScala());
            task.setOutput(dir);

            // must get replacements from extension afterEValuate()

            ScalaCompile compile = (ScalaCompile) project.getTasks().getByName(main.getCompileTaskName("scala"));
            compile.dependsOn("sourceMainScala");
            compile.setSource(dir);
        }

        // groovy!!!
        if (project.getPlugins().hasPlugin("groovy"))
        {
            GroovySourceSet set = (GroovySourceSet) new DslObject(main).getConvention().getPlugins().get("groovy");
            File dir = new File(project.getBuildDir(), SourceSet.MAIN_SOURCE_SET_NAME + "/groovy");

            task = makeTask("sourceMainGroovy", TaskSourceCopy.class);
            task.setSource(set.getGroovy());
            task.setOutput(dir);

            // must get replacements from extension afterEValuate()

            GroovyCompile compile = (GroovyCompile) project.getTasks().getByName(main.getCompileTaskName("groovy"));
            compile.dependsOn("sourceMainGroovy");
            compile.setSource(dir);
        }

        // Todo: kotlin?  closure?
    }

    protected void mapConfigurations()
    {
        if (project.getPlugins().hasPlugin("maven"))
        {
            MavenPluginConvention mavenConv = (MavenPluginConvention) project.getConvention().getPlugins().get("maven");
            Conf2ScopeMappingContainer mappings = mavenConv.getConf2ScopeMappings();
            ConfigurationContainer configs = project.getConfigurations();
            final int priority = 500; // 500 is more than the compile config which is at 300

            mappings.setSkipUnmappedConfs(true); // dont want unmapped confs bieng compile deps..
            mappings.addMapping(priority, configs.getByName(CONFIG_PROVIDED), Conf2ScopeMappingContainer.PROVIDED);
            // TODO: more configs?
            // TODO: UNTESTED
        }
    }

    protected void addAtsToDeobf()
    {

        // add src ATs
        DeobfuscateJar binDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN);
        DeobfuscateJar decompDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addTransformer(extAts);
        decompDeobf.addTransformer(extAts);

        // from the resources dirs
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            SourceSet main = javaConv.getSourceSets().getByName("main");
            SourceSet api = javaConv.getSourceSets().getByName("api");

            boolean addedAts = false;

            for (File at : main.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in main resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                    addedAts = true;
                }
            }

            for (File at : api.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in api resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                    addedAts = true;
                }
            }

            useLocalCache = useLocalCache || addedAts;
        }
    }
}

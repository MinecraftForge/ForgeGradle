package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.GenEclipseRunTask;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.tasks.RemapSources;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.gradle.util.delayed.TokenReplacer;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
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
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

public abstract class UserBasePlugin<T extends UserExtension> extends BasePlugin<T>
{
    private boolean madeDecompTasks = false; // to gaurd against stupid programmers

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

        // IDE stuff
        addEclipseRuns();
        configureIntellij();

        applyUserPlugin();
    }

    @Override
    protected void afterEvaluate()
    {
        // to gaurd against stupid programmers
        if (!madeDecompTasks)
        {
            throw new RuntimeException("THE DECOMP TASKS HAVENT BEEN MADE!! STUPID FORGEGRADLE DEVELOPER!!!! :(");
        }

        super.afterEvaluate();

        // add repalcements for run configs and gradle start
        T ext = getExtension();
        TokenReplacer.putReplacement(REPLACE_CLIENT_TWEAKER, getClientTweaker(ext));
        TokenReplacer.putReplacement(REPLACE_SERVER_TWEAKER, getServerTweaker(ext));
        TokenReplacer.putReplacement(REPLACE_CLIENT_MAIN, getClientRunClass(ext));
        TokenReplacer.putReplacement(REPLACE_SERVER_MAIN, getServerRunClass(ext));

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

        // add reobf srg lines
        ((TaskSingleReobf) project.getTasks().getByName(TASK_REOBF)).addExtraSrgLines(getExtension().getSrgExtra());
        
        // add task depends for reobf
        if (project.getPlugins().hasPlugin("maven"))
        {
            project.getTasks().getByName("uploadArchives").dependsOn(TASK_REOBF);
        }

        // TODO: do some GradleSTart stuff based on the MC version?
    }

    protected abstract void applyUserPlugin();

    protected void tasksClient(String globalPattern, String localPattern)
    {
        makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_CLIENT_FRESH), TASK_DL_CLIENT, delayedFile(MCP_PATCHES_CLIENT));
    }

    protected void tasksServer(String globalPattern, String localPattern)
    {
        makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_SERVER_FRESH), TASK_DL_SERVER, delayedFile(MCP_PATCHES_SERVER));
    }

    protected void tasksMerged(String globalPattern, String localPattern)
    {
        makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_MERGED), TASK_MERGE_JARS, delayedFile(MCP_PATCHES_MERGED));
    }

    private void makeDecompTasks(final String globalPattern, final String localPattern, Object inputJar, String inputTask, Object mcpPatchSet)
    {
        madeDecompTasks = true; // to gaurd against stupid programmers

        final DeobfuscateJar deobfBin = makeTask(TASK_DEOBF_BIN, DeobfuscateJar.class);
        {
            deobfBin.setSrg(delayedFile(SRG_NOTCH_TO_MCP));
            deobfBin.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfBin.setExceptorCfg(delayedFile(EXC_MCP));
            deobfBin.setFieldCsv(delayedFile(CSV_FIELD));
            deobfBin.setMethodCsv(delayedFile(CSV_METHOD));
            deobfBin.setApplyMarkers(false);
            deobfBin.setInJar(inputJar);
            deobfBin.setOutJar(chooseDeobfOutput(globalPattern, localPattern, "Bin", ""));
            deobfBin.dependsOn(inputTask, TASK_GENERATE_SRGS);
        }

        final Object deobfDecompJar = chooseDeobfOutput(globalPattern, localPattern, "", "srgBin");
        final Object decompJar = chooseDeobfOutput(globalPattern, localPattern, "", "decomp");
        final Object postDecompJar = chooseDeobfOutput(globalPattern, localPattern, "", "decompFixed");
        final Object remapped = chooseDeobfOutput(globalPattern, localPattern, "Src", "sources");
        final Object recompiledJar = chooseDeobfOutput(globalPattern, localPattern, "Src", "");

        final DeobfuscateJar deobfDecomp = makeTask(TASK_DEOBF, DeobfuscateJar.class);
        {
            deobfDecomp.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfDecomp.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfDecomp.setExceptorCfg(delayedFile(EXC_SRG));
            deobfDecomp.setApplyMarkers(true);
            deobfDecomp.setInJar(inputJar);
            deobfDecomp.setOutJar(deobfDecompJar);
            deobfDecomp.dependsOn(inputTask, TASK_GENERATE_SRGS); // todo grab correct task to depend on
        }

        final ApplyFernFlowerTask decompile = makeTask(TASK_DECOMPILE, ApplyFernFlowerTask.class);
        {
            decompile.setInJar(deobfDecompJar);
            decompile.setOutJar(decompJar);
            decompile.setFernflower(delayedFile(JAR_FERNFLOWER));
            decompile.dependsOn(TASK_DL_FERNFLOWER, deobfDecomp);
        }

        final PostDecompileTask postDecomp = makeTask(TASK_POST_DECOMP, PostDecompileTask.class);
        {
            postDecomp.setInJar(decompJar);
            postDecomp.setOutJar(postDecompJar);
            postDecomp.setPatches(mcpPatchSet);
            postDecomp.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecomp.dependsOn(decompile);
        }

        final RemapSources remap = makeTask(TASK_REMAP, RemapSources.class);
        {
            remap.setInJar(postDecompJar);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(CSV_FIELD));
            remap.setMethodsCsv(delayedFile(CSV_METHOD));
            remap.setParamsCsv(delayedFile(CSV_PARAM));
            remap.dependsOn(postDecomp);
        }

        final TaskRecompileMc recompile = makeTask(TASK_RECOMPILE, TaskRecompileMc.class);
        {
            recompile.setInSources(remapped);
            recompile.setClasspath(CONFIG_MC_DEPS);
            recompile.setOutJar(recompiledJar);

            recompile.dependsOn(remap, TASK_DL_VERSION_JSON);
        }

        // create GradleStart
        final CreateStartTask makeStart = makeTask(TASK_MAKE_START, CreateStartTask.class);
        {
            for (String resource : GRADLE_START_RESOURCES)
            {
                makeStart.addResource(resource);
            }

            makeStart.addReplacement("@@MCVERSION@@", delayedString(REPLACE_MC_VERSION));
            makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
            makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(REPLACE_CACHE_DIR + "/assets"));
            makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(DIR_NATIVES));
            makeStart.addReplacement("@@SRGDIR@@", delayedFile(DIR_MCP_MAPPINGS + "/srgs/"));
            makeStart.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(SRG_NOTCH_TO_SRG));
            makeStart.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(SRG_NOTCH_TO_MCP));
            makeStart.addReplacement("@@SRG_SRG_MCP@@", delayedFile(SRG_SRG_TO_MCP));
            makeStart.addReplacement("@@SRG_MCP_SRG@@", delayedFile(SRG_MCP_TO_SRG));
            makeStart.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(SRG_MCP_TO_NOTCH));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_DATA));
            makeStart.addReplacement("@@CLIENTTWEAKER@@", delayedString(REPLACE_CLIENT_TWEAKER));
            makeStart.addReplacement("@@SERVERTWEAKER@@", delayedString(REPLACE_SERVER_TWEAKER));
            makeStart.addReplacement("@@BOUNCERCLIENT@@", delayedString(REPLACE_CLIENT_MAIN));
            makeStart.addReplacement("@@BOUNCERSERVER@@", delayedString(REPLACE_SERVER_MAIN));
            makeStart.setStartOut(getStartDir());
            makeStart.addClasspathConfig(CONFIG_MC_DEPS);

            // see delayed task config for some more config regarding MC versions... for 1.7.10 compat
            // TODO: UNTESTED

            makeStart.mustRunAfter(deobfBin, recompile);

            makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS, TASK_EXTRACT_NATIVES);
        }

        // create reobf task
        TaskSingleReobf reobf = makeTask(TASK_REOBF, TaskSingleReobf.class);
        {
            reobf.setExceptorCfg(delayedFile(EXC_SRG));
            reobf.setFieldCsv(delayedFile(CSV_FIELD));
            reobf.setMethodCsv(delayedFile(CSV_METHOD));
            reobf.setDeobfFile(deobfDecompJar);
            reobf.setRecompFile(recompiledJar);

            reobf.dependsOn(TASK_GENERATE_SRGS);
            reobf.mustRunAfter("test");

            // TODO: IMPLEMENT IN SUBLCASSES
            //task.setSrg(delayedFile(REOBF_SRG));
            //task.setMcVersion(delayedString(Constants.REPLACE_MC_VERSION));
        }

        // add setup dependencies
        project.getTasks().getByName(TASK_SETUP_CI).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DEV).dependsOn(deobfBin, makeStart);
        project.getTasks().getByName(TASK_SETUP_DECOMP).dependsOn(recompile, makeStart);

        // add build task depends
        project.getTasks().getByName("build").dependsOn(reobf);
        project.getTasks().getByName("assemble").dependsOn(reobf);

        // make dummy task for MC dep
        final TaskDepDummy dummy = makeTask(TASK_DUMMY_MC, TaskDepDummy.class);
        dummy.setOutputFile(delayedFile(JAR_DUMMY_MC));
        project.getDependencies().add(CONFIG_MC, project.files(delayedFile(JAR_DUMMY_MC)).builtBy(dummy));

        // configure MC compiling. This AfterEvaluate section should happen after the one made in
        // also configure the dummy task dependencies
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                boolean isDecomp = false;

                if (project.file(recompiledJar).exists())
                {
                    isDecomp = true;
                }

                List<String> tasks = project.getGradle().getStartParameter().getTaskNames();
                if (tasks.contains(TASK_DEOBF_BIN) || tasks.contains(TASK_SETUP_CI) || tasks.contains(TASK_SETUP_DEV))
                {
                    isDecomp = false;
                    dummy.dependsOn(deobfBin);
                    dummy.mustRunAfter(TASK_SETUP_CI, TASK_SETUP_DEV);
                }
                else if (tasks.contains(TASK_RECOMPILE) || tasks.contains(TASK_SETUP_DECOMP))
                {
                    isDecomp = true;
                    dummy.dependsOn(recompile);
                    dummy.mustRunAfter(TASK_SETUP_DECOMP);
                }

                afterDecomp(isDecomp, useLocalCache(getExtension()), CONFIG_MC);
            }
        });
    }

    /**
     * This method returns an object that resolved to the correct pattern based on the useLocalCache() method
     * @return useable deobfsucated output file
     */
    @SuppressWarnings("serial")
    private Object chooseDeobfOutput(final String globalPattern, final String localPattern, final String appendage, final String classifier)
    {
        return new Closure<DelayedFile>(project, this) {
            public DelayedFile call()
            {
                String classAdd = Strings.isNullOrEmpty(classifier) ? "" : "-"+classifier;
                String str = useLocalCache(getExtension()) ? localPattern : globalPattern;
                return delayedFile(String.format(str, appendage) + classAdd + ".jar");
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
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));
        main.setCompileClasspath(main.getCompileClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));
        main.setRuntimeClasspath(main.getCompileClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_START)));
        test.setCompileClasspath(test.getCompileClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_PROVIDED)));
        test.setRuntimeClasspath(test.getRuntimeClasspath()
                .plus(api.getOutput())
                .plus(project.getConfigurations().getByName(CONFIG_MC))
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS))
                .plus(project.getConfigurations().getByName(CONFIG_START)));

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
        // INFO: This method is overriden by PatcherUserBasePlugin to add the reading from resource dirs and depndencies
        
        // add src ATs
        DeobfuscateJar binDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN);
        DeobfuscateJar decompDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addTransformer(extAts);
        decompDeobf.addTransformer(extAts);
    }

    /**
     * This method should add the MC dependency to the supplied config, as well as do any extra configuration that requires the provided information.
     * @param isDecomp Whether to use the recmpield MC artifact
     * @param useLocalCache Whetehr or not ATs were applied to this artifact
     * @param mcConfig Which gradle configuration to add the MC dep to
     */
    protected abstract void afterDecomp(boolean isDecomp, boolean useLocalCache, String mcConfig);

    /**
     * This method is called early, and not late.
     * @return TRUE if a server run config and GradleStartServer should be created.
     */
    protected abstract boolean hasServerRun();

    /**
     * This method is called early, and not late.
     * @return TRUE if a client run config and GradleStart should be created.
     */
    protected abstract boolean hasClientRun();

    /**
     * The location where the GradleStart files will be generated to.
     * @return object that resolves to a file
     */
    protected abstract Object getStartDir();

    /**
     * To be inserted into GradleStart. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty string if no tweaker. NEVER NULL.
     */
    protected abstract String getClientTweaker(T ext);

    /**
     * To be inserted into GradleStartServer. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty string if no tweaker. NEVER NULL.
     */
    protected abstract String getServerTweaker(T ext);

    /**
     * To be inserted into GradleStart. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty string if default launchwrapper. NEVER NULL.
     */
    protected abstract String getClientRunClass(T ext);

    /**
     * For run configurations. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty list for no arguments. NEVER NULL.
     */
    protected abstract List<String> getClientRunArgs(T ext);

    /**
     * To be inserted into GradleStartServer. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty string if default launchwrapper. NEVER NULL.
     */
    protected abstract String getServerRunClass(T ext);

    /**
     * For run configurations. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty list for no arguments. NEVER NULL.
     */
    protected abstract List<String> getServerRunArgs(T ext);

    /**
     * Creates task that generate the eclipse run configs and attaches them to the eclipse task.
     */
    protected void addEclipseRuns()
    {
        if (this.hasClientRun())
        {
            GenEclipseRunTask eclipseClient = makeTask("makeEclipseCleanRunClient", GenEclipseRunTask.class);
            eclipseClient.setMainClass(GRADLE_START_CLIENT);
            eclipseClient.setProjectName(project.getName());
            eclipseClient.setOutputFile(project.file("Client.launch"));
            eclipseClient.setRunDir(delayedFile(REPLACE_RUN_DIR));
            eclipseClient.dependsOn(TASK_MAKE_START);

            project.getTasks().getByName("eclipse").dependsOn(eclipseClient);
        }

        if (this.hasServerRun())
        {
            GenEclipseRunTask eclipseServer = makeTask("makeEclipseCleanRunServer", GenEclipseRunTask.class);
            eclipseServer.setMainClass(GRADLE_START_SERVER);
            eclipseServer.setProjectName(project.getName());
            eclipseServer.setOutputFile(project.file("Server.launch"));
            eclipseServer.setRunDir(delayedFile(REPLACE_RUN_DIR));
            eclipseServer.dependsOn(TASK_MAKE_START);

            project.getTasks().getByName("eclipse").dependsOn(eclipseServer);
        }

    }

    /**
     * Adds the intellij run configs and makes a few other tweaks to the intellij project creation
     */
    @SuppressWarnings("serial")
    protected void configureIntellij()
    {
        IdeaModel ideaConv = (IdeaModel) project.getExtensions().getByName("idea");

        ideaConv.getModule().getExcludeDirs().addAll(project.files(".gradle", "build", ".idea", "out").getFiles());
        ideaConv.getModule().setDownloadJavadoc(true);
        ideaConv.getModule().setDownloadSources(true);

        // fix the idea bug
        ideaConv.getModule().setInheritOutputDirs(true);

        Task task = makeTask("genIntellijRuns", DefaultTask.class);
        task.doLast(new Action<Task>() {
            @Override
            public void execute(Task task)
            {
                try
                {
                    String module = task.getProject().getProjectDir().getCanonicalPath();

                    File root = task.getProject().getProjectDir().getCanonicalFile();
                    File file = null;
                    while (file == null && !root.equals(task.getProject().getRootProject().getProjectDir().getCanonicalFile().getParentFile()))
                    {
                        file = new File(root, ".idea/workspace.xml");
                        if (!file.exists())
                        {
                            file = null;
                            // find iws file
                            for (File f : root.listFiles())
                            {
                                if (f.isFile() && f.getName().endsWith(".iws"))
                                {
                                    file = f;
                                    break;
                                }
                            }
                        }

                        root = root.getParentFile();
                    }

                    if (file == null || !file.exists())
                        throw new RuntimeException("Intellij workspace file could not be found! are you sure you imported the project into intellij?");

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
        task.setGroup(GROUP_FG);
        task.setDescription("Generates the ForgeGradle run confgiurations for intellij Idea");

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

        T ext = getExtension();

        String[][] config = new String[][]
        {
                this.hasClientRun() ? new String[]
                {
                        "Minecraft Client",
                        GRADLE_START_CLIENT,
                        Joiner.on(' ').join(getClientRunArgs(ext))
                } : null,

                this.hasServerRun() ? new String[]
                {
                        "Minecraft Server",
                        GRADLE_START_SERVER,
                        Joiner.on(' ').join(getServerRunArgs(ext))
                } : null
        };

        for (String[] data : config)
        {
            if (data == null)
                continue;

            Element child = addXml(root, "configuration", ImmutableMap.of(
                    "default", "false",
                    "name", data[0],
                    "type", "Application",
                    "factoryName", "Application",
                    "default", "false"));

            addXml(child, "extension", ImmutableMap.of(
                    "name", "coverage",
                    "enabled", "false",
                    "sample_coverage", "true",
                    "runner", "idea"));
            addXml(child, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", data[1]));
            addXml(child, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", ""));
            addXml(child, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", data[3]));
            addXml(child, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", "file://" + delayedFile("{RUN_DIR}").call().getCanonicalPath().replace(module, "$PROJECT_DIR$")));
            addXml(child, "option", ImmutableMap.of("name", "ALTERNATIVE_JRE_PATH_ENABLED", "value", "false"));
            addXml(child, "option", ImmutableMap.of("name", "ALTERNATIVE_JRE_PATH", "value", ""));
            addXml(child, "option", ImmutableMap.of("name", "ENABLE_SWING_INSPECTOR", "value", "false"));
            addXml(child, "option", ImmutableMap.of("name", "ENV_VARIABLES"));
            addXml(child, "option", ImmutableMap.of("name", "PASS_PARENT_ENVS", "value", "true"));
            addXml(child, "module", ImmutableMap.of("name", ((IdeaModel) project.getExtensions().getByName("idea")).getModule().getName()));
            addXml(child, "RunnerSettings", ImmutableMap.of("RunnerId", "Run"));
            addXml(child, "ConfigurationWrapper", ImmutableMap.of("RunnerId", "Run"));
        }

        File f = delayedFile(REPLACE_RUN_DIR).call();
        if (!f.exists())
            f.mkdirs();
    }
}

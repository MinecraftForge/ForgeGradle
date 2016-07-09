/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.XmlProvider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.plugins.MavenPluginConvention;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.GroovySourceSet;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.ScalaSourceSet;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.GroovyCompile;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.model.IdeaModel;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.ApplyS2STask;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.ExtractS2SRangeTask;
import net.minecraftforge.gradle.tasks.GenEclipseRunTask;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.tasks.RemapSources;
import net.minecraftforge.gradle.user.ReobfTaskFactory.ReobfTaskWrapper;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

public abstract class UserBasePlugin<T extends UserBaseExtension> extends BasePlugin<T>
{
    private boolean madeDecompTasks = false; // to gaurd against stupid programmers
    private final Closure<Object> makeRunDir = new Closure<Object>(null, null) {
        public Object call()
        {
            delayedFile(REPLACE_RUN_DIR).call().mkdirs();
            return null;
        }
    };

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
        task.dependsOn(TASK_DD_PROVIDED, TASK_DD_COMPILE);

        task = makeTask(TASK_SETUP_DEV, DefaultTask.class);
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");
        task.dependsOn(TASK_DD_PROVIDED, TASK_DD_COMPILE);

        task = makeTask(TASK_SETUP_DECOMP, DefaultTask.class);
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");
        task.dependsOn(TASK_DD_PROVIDED, TASK_DD_COMPILE);

        // create configs
        project.getConfigurations().maybeCreate(CONFIG_MC);
        project.getConfigurations().maybeCreate(CONFIG_PROVIDED);
        project.getConfigurations().maybeCreate(CONFIG_START);

        project.getConfigurations().maybeCreate(CONFIG_DEOBF_COMPILE);
        project.getConfigurations().maybeCreate(CONFIG_DEOBF_PROVIDED);
        project.getConfigurations().maybeCreate(CONFIG_DC_RESOLVED);
        project.getConfigurations().maybeCreate(CONFIG_DP_RESOLVED);

        // create the reobf named container
        NamedDomainObjectContainer<IReobfuscator> reobf = project.container(IReobfuscator.class, new ReobfTaskFactory(this));
        project.getExtensions().add(EXT_REOBF, reobf);

        configureCompilation();

        // Quality of life stuff for the users
        createSourceCopyTasks();
        doDevTimeDeobf();
        doDepAtExtraction();
        configureRetromapping();
        makeRunTasks();

        // IDE stuff
        configureEclipse();
        configureIntellij();

        applyUserPlugin();
    }

    @Override
    protected void afterEvaluate()
    {
        // to guard against stupid programmers
        if (!madeDecompTasks)
        {
            throw new RuntimeException("THE DECOMP TASKS HAVENT BEEN MADE!! STUPID FORGEGRADLE DEVELOPER!!!! :(");
        }

        // verify runDir is set
        if (Strings.isNullOrEmpty(getExtension().getRunDir()))
        {
            throw new GradleConfigurationException("RunDir is not set!");
        }

        super.afterEvaluate();

        // add replacements for run configs and gradle start
        T ext = getExtension();
        replacer.putReplacement(REPLACE_CLIENT_TWEAKER, getClientTweaker(ext));
        replacer.putReplacement(REPLACE_SERVER_TWEAKER, getServerTweaker(ext));
        replacer.putReplacement(REPLACE_CLIENT_MAIN, getClientRunClass(ext));
        replacer.putReplacement(REPLACE_SERVER_MAIN, getServerRunClass(ext));

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

        if (ext.getMakeObfSourceJar())
        {
            project.getTasks().getByName("assemble").dependsOn(TASK_SRC_JAR);
        }

        // add task depends for reobf
        if (project.getPlugins().hasPlugin("maven"))
        {
            project.getTasks().getByName("uploadArchives").dependsOn(TASK_REOBF);

            if (ext.getMakeObfSourceJar())
            {
                project.getTasks().getByName("uploadArchives").dependsOn(TASK_SRC_JAR);
                project.getArtifacts().add("archives", project.getTasks().getByName(TASK_SRC_JAR));
            }
        }

        // add GradleStart dep
        {
            ConfigurableFileCollection col = project.files(getStartDir());
            col.builtBy(TASK_MAKE_START);
            project.getDependencies().add(CONFIG_START, col);
        }
        // TODO: do some GradleStart stuff based on the MC version?

        // run task stuff
        // Add the mod and stuff to the classpath of the exec tasks.
        final Jar jarTask = (Jar) project.getTasks().getByName("jar");

        if (this.hasClientRun())
        {
            JavaExec exec = (JavaExec) project.getTasks().getByName("runClient");
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(project.getConfigurations().getByName(CONFIG_MC));
            exec.classpath(project.getConfigurations().getByName(CONFIG_MC_DEPS));
            exec.classpath(project.getConfigurations().getByName(CONFIG_START));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
            exec.jvmArgs(getClientJvmArgs(getExtension()));
            exec.args(getClientRunArgs(getExtension()));
        }

        if (this.hasServerRun())
        {
            JavaExec exec = (JavaExec) project.getTasks().getByName("runServer");
            exec.classpath(project.getConfigurations().getByName("runtime"));
            exec.classpath(project.getConfigurations().getByName(CONFIG_MC));
            exec.classpath(project.getConfigurations().getByName(CONFIG_MC_DEPS));
            exec.classpath(project.getConfigurations().getByName(CONFIG_START));
            exec.classpath(jarTask.getArchivePath());
            exec.dependsOn(jarTask);
            exec.jvmArgs(getServerJvmArgs(getExtension()));
            exec.jvmArgs(getServerRunArgs(getExtension()));
        }

        // complain about version number
        // blame cazzar if this regex doesnt work
        Pattern pattern = Pattern.compile("(?:(?:mc)?((?:\\d+)(?:.\\d+)+)-)?((?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*))+)(?:-([\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?(?:\\+([\\da-z\\-]+(?:\\.[\\da-z\\-]+)*))?", Pattern.CASE_INSENSITIVE);
        if (!pattern.matcher(project.getVersion().toString()).matches())
        {
            project.getLogger().warn("Version string '"+project.getVersion()+"' does not match SemVer specification ");
            project.getLogger().warn("You should try SemVer : http://semver.org/");
        }
    }

    protected abstract void applyUserPlugin();

    /**
     * Sets up the default settings for reobf tasks.
     *
     * @param reobf The task to setup
     */
    protected void setupReobf(ReobfTaskWrapper reobf)
    {
        TaskSingleReobf task = reobf.getTask();
        task.setExceptorCfg(delayedFile(EXC_SRG));
        task.setFieldCsv(delayedFile(CSV_FIELD));
        task.setMethodCsv(delayedFile(CSV_METHOD));

        reobf.setMappingType(ReobfMappingType.NOTCH);
        JavaPluginConvention java = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        reobf.setClasspath(java.getSourceSets().getByName("main").getCompileClasspath());
    }

    @SuppressWarnings("unchecked")
    protected void makeDecompTasks(final String globalPattern, final String localPattern, Object inputJar, String inputTask, Object mcpPatchSet, Object mcpInject)
    {
        madeDecompTasks = true; // to guard against stupid programmers

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
            deobfBin.dependsOn(inputTask, TASK_GENERATE_SRGS, TASK_EXTRACT_DEP_ATS, TASK_DD_COMPILE, TASK_DD_PROVIDED);
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
            deobfDecomp.dependsOn(inputTask, TASK_GENERATE_SRGS, TASK_EXTRACT_DEP_ATS, TASK_DD_COMPILE, TASK_DD_PROVIDED); // todo grab correct task to depend on
        }

        final ApplyFernFlowerTask decompile = makeTask(TASK_DECOMPILE, ApplyFernFlowerTask.class);
        {
            decompile.setInJar(deobfDecompJar);
            decompile.setOutJar(decompJar);
            decompile.setClasspath(project.getConfigurations().getByName(Constants.CONFIG_MC_DEPS));
            decompile.dependsOn(deobfDecomp);
        }

        final PostDecompileTask postDecomp = makeTask(TASK_POST_DECOMP, PostDecompileTask.class);
        {
            postDecomp.setInJar(decompJar);
            postDecomp.setOutJar(postDecompJar);
            postDecomp.setPatches(mcpPatchSet);
            postDecomp.setInjects(mcpInject);
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
            makeStart.addResource(GRADLE_START_RESOURCES[2]); // gradle start common.

            if (this.hasClientRun())
            {
                makeStart.addResource(GRADLE_START_RESOURCES[0]); // gradle start

                makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
                makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(REPLACE_CACHE_DIR + "/assets"));
                makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(DIR_NATIVES));
                makeStart.addReplacement("@@TWEAKERCLIENT@@", delayedString(REPLACE_CLIENT_TWEAKER));
                makeStart.addReplacement("@@BOUNCERCLIENT@@", delayedString(REPLACE_CLIENT_MAIN));

                makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS, TASK_EXTRACT_NATIVES);
            }

            if (this.hasServerRun())
            {
                makeStart.addResource(GRADLE_START_RESOURCES[1]); // gradle start

                makeStart.addReplacement("@@TWEAKERSERVER@@", delayedString(REPLACE_SERVER_TWEAKER));
                makeStart.addReplacement("@@BOUNCERSERVER@@", delayedString(REPLACE_SERVER_MAIN));
            }

            makeStart.addReplacement("@@MCVERSION@@", delayedString(REPLACE_MC_VERSION));
            makeStart.addReplacement("@@SRGDIR@@", delayedFile(DIR_MCP_MAPPINGS + "/srgs/"));
            makeStart.addReplacement("@@SRG_NOTCH_SRG@@", delayedFile(SRG_NOTCH_TO_SRG));
            makeStart.addReplacement("@@SRG_NOTCH_MCP@@", delayedFile(SRG_NOTCH_TO_MCP));
            makeStart.addReplacement("@@SRG_SRG_MCP@@", delayedFile(SRG_SRG_TO_MCP));
            makeStart.addReplacement("@@SRG_MCP_SRG@@", delayedFile(SRG_MCP_TO_SRG));
            makeStart.addReplacement("@@SRG_MCP_NOTCH@@", delayedFile(SRG_MCP_TO_NOTCH));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_MAPPINGS));
            makeStart.setStartOut(getStartDir());
            makeStart.addClasspathConfig(CONFIG_MC_DEPS);
            makeStart.mustRunAfter(deobfBin, recompile);
        }

        // setup reobf...
        ((NamedDomainObjectContainer<IReobfuscator>) project.getExtensions().getByName(EXT_REOBF)).create("jar");

        // add setup dependencies
        project.getTasks().getByName(TASK_SETUP_CI).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DEV).dependsOn(deobfBin, makeStart);
        project.getTasks().getByName(TASK_SETUP_DECOMP).dependsOn(recompile, makeStart);

        // configure MC compiling. This AfterEvaluate section should happen after the one made in
        // also configure the dummy task dependencies
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                if (project.getState().getFailure() != null)
                    return;

                // the recompiled jar exists, or the decomp task is part of the build
                boolean isDecomp = project.file(recompiledJar).exists() || project.getGradle().getStartParameter().getTaskNames().contains(TASK_SETUP_DECOMP);

                // set task dependencies
                if (!isDecomp)
                {
                    project.getTasks().getByName("compileJava").dependsOn(UserConstants.TASK_DEOBF_BIN);
                    project.getTasks().getByName("compileApiJava").dependsOn(UserConstants.TASK_DEOBF_BIN);
                }

                afterDecomp(isDecomp, useLocalCache(getExtension()), CONFIG_MC);
            }
        });
    }

    /**
     * This method returns an object that resolved to the correct pattern based on the useLocalCache() method
     *
     * @param globalPattern The global pattern
     * @param localPattern  The local pattern
     * @param appendage     The appendage
     * @param classifier    The classifier
     *
     * @return useable deobfsucated output file
     */
    @SuppressWarnings("serial")
    protected final Object chooseDeobfOutput(final String globalPattern, final String localPattern, final String appendage, final String classifier)
    {
        return new Closure<DelayedFile>(project, this) {
            public DelayedFile call()
            {
                String classAdd = Strings.isNullOrEmpty(classifier) ? "" : "-" + classifier;
                String str = useLocalCache(getExtension()) ? localPattern : globalPattern;
                return delayedFile(String.format(str, appendage) + classAdd + ".jar");
            }
        };
    }

    /**
     * A boolean used to cache the output of useLocalCache;
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
        useLocalCache = !extension.getAccessTransformers().isEmpty() || extension.isUseDepAts();

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
                .plus(project.getConfigurations().getByName(CONFIG_MC_DEPS)));

        project.getConfigurations().getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).extendsFrom(project.getConfigurations().getByName(CONFIG_DC_RESOLVED));
        project.getConfigurations().getByName(CONFIG_PROVIDED).extendsFrom(project.getConfigurations().getByName(CONFIG_DP_RESOLVED));
        project.getConfigurations().getByName(api.getCompileConfigurationName()).extendsFrom(project.getConfigurations().getByName("compile"));
        project.getConfigurations().getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME).extendsFrom(project.getConfigurations().getByName("apiCompile"));

        Javadoc javadoc = (Javadoc) project.getTasks().getByName(JavaPlugin.JAVADOC_TASK_NAME);
        javadoc.setClasspath(main.getOutput().plus(main.getCompileClasspath()));

        // libs folder dependencies
        project.getDependencies().add(JavaPlugin.COMPILE_CONFIGURATION_NAME, project.fileTree("libs"));

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

        Action<SourceSet> action = new Action<SourceSet>() {
            @Override
            public void execute(SourceSet set) {

                TaskSourceCopy task;

                String capName = set.getName().substring(0, 1).toUpperCase() + set.getName().substring(1);
                String taskPrefix = "source"+capName;
                File dirRoot = new File(project.getBuildDir(), "sources/"+set.getName());

                // java
                {
                    File dir = new File(dirRoot, "java");

                    task = makeTask(taskPrefix+"Java", TaskSourceCopy.class);
                    task.setSource(set.getJava());
                    task.setOutput(dir);

                    // must get replacements from extension afterEvaluate()

                    JavaCompile compile = (JavaCompile) project.getTasks().getByName(set.getCompileJavaTaskName());
                    compile.dependsOn(task);
                    compile.setSource(dir);
                }

                // scala
                if (project.getPlugins().hasPlugin("scala"))
                {
                    ScalaSourceSet langSet = (ScalaSourceSet) new DslObject(set).getConvention().getPlugins().get("scala");
                    File dir = new File(dirRoot, "scala");

                    task = makeTask(taskPrefix+"Scala", TaskSourceCopy.class);
                    task.setSource(langSet.getScala());
                    task.setOutput(dir);

                    // must get replacements from extension afterEValuate()

                    ScalaCompile compile = (ScalaCompile) project.getTasks().getByName(set.getCompileTaskName("scala"));
                    compile.dependsOn(task);
                    compile.setSource(dir);
                }

                // groovy
                if (project.getPlugins().hasPlugin("groovy"))
                {
                    GroovySourceSet langSet = (GroovySourceSet) new DslObject(set).getConvention().getPlugins().get("groovy");
                    File dir = new File(dirRoot, "groovy");

                    task = makeTask(taskPrefix+"Groovy", TaskSourceCopy.class);
                    task.setSource(langSet.getGroovy());
                    task.setOutput(dir);

                    // must get replacements from extension afterEValuate()

                    GroovyCompile compile = (GroovyCompile) project.getTasks().getByName(set.getCompileTaskName("groovy"));
                    compile.dependsOn(task);
                    compile.setSource(dir);
                }
            }
        };

        // for existing sourceSets
        for (SourceSet set : javaConv.getSourceSets())
        {
            action.execute(set);
        }
        // for user-defined ones
        javaConv.getSourceSets().whenObjectAdded(action);
    }

    protected final void doDevTimeDeobf()
    {
        final Task compileDummy = getDummyDep("compile", delayedFile(DIR_DEOBF_DEPS + "/compileDummy.jar"), TASK_DD_COMPILE);
        final Task providedDummy = getDummyDep("compile", delayedFile(DIR_DEOBF_DEPS + "/providedDummy.jar"), TASK_DD_PROVIDED);

        setupDevTimeDeobf(compileDummy, providedDummy);
    }

    protected void setupDevTimeDeobf(final Task compileDummy, final Task providedDummy)
    {
        // die wih error if I find invalid types...
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                if (project.getState().getFailure() != null)
                    return;

                // add maven repo
                addMavenRepo(project, "deobfDeps", delayedFile(DIR_DEOBF_DEPS).call().getAbsoluteFile().toURI().getPath());

                remapDeps(project, project.getConfigurations().getByName(CONFIG_DEOBF_COMPILE), CONFIG_DC_RESOLVED, compileDummy);
                remapDeps(project, project.getConfigurations().getByName(CONFIG_DEOBF_PROVIDED), CONFIG_DP_RESOLVED, providedDummy);
            }
        });
    }

    @SuppressWarnings("unchecked")
    protected void remapDeps(Project project, Configuration config, String resolvedConfig, Task dummyTask)
    {
        // only allow maven/ivy dependencies
        for (Dependency dep : config.getIncoming().getDependencies())
        {
            if (!(dep instanceof ExternalModuleDependency))
            {
                throw new GradleConfigurationException("Only allowed to use maven dependencies for this. If its a jar file, deobfuscate it yourself.");
            }
        }

        int taskId = 0;

        // FOR SOURCES!

        HashMap<ComponentIdentifier, ModuleVersionIdentifier> idMap = Maps.newHashMap();

        // FOR BINARIES
        for (ResolvedArtifact artifact : config.getResolvedConfiguration().getResolvedArtifacts())
        {
            ModuleVersionIdentifier module = artifact.getModuleVersion().getId();
            String group = "deobf." + module.getGroup();

            // Add artifacts that will be remapped to get their sources
            idMap.put(artifact.getId().getComponentIdentifier(), module);

            TaskSingleDeobfBin deobf = makeTask(config.getName() + "DeobfDepTask" + (taskId++), TaskSingleDeobfBin.class);
            deobf.setInJar(artifact.getFile());
            deobf.setOutJar(getFile(DIR_DEOBF_DEPS, group, module.getName(), module.getVersion(), null));
            deobf.setFieldCsv(delayedFile(CSV_FIELD));
            deobf.setMethodCsv(delayedFile(CSV_METHOD));
            deobf.dependsOn(TASK_EXTRACT_MAPPINGS);
            dummyTask.dependsOn(deobf);

            project.getDependencies().add(resolvedConfig, group + ":" + module.getName() + ":" + module.getVersion());
        }

        for (DependencyResult depResult : config.getIncoming().getResolutionResult().getAllDependencies())
        {
            idMap.put(depResult.getFrom().getId(), depResult.getFrom().getModuleVersion());
        }

        ArtifactResolutionResult result = project.getDependencies().createArtifactResolutionQuery()
                .forComponents(idMap.keySet())
                .withArtifacts(JvmLibrary.class, SourcesArtifact.class)
                .execute();

        for (ComponentArtifactsResult comp : result.getResolvedComponents())
        {
            ModuleVersionIdentifier module = idMap.get(comp.getId());
            String group = "deobf." + module.getGroup();

            for (ArtifactResult art : comp.getArtifacts(SourcesArtifact.class))
            {
                // there can only be One!
                RemapSources remap = makeTask(config.getName() + "RemapDepSourcesTask" + (taskId++), RemapSources.class);
                remap.setInJar(((ResolvedArtifactResult) art).getFile());
                remap.setOutJar(getFile(DIR_DEOBF_DEPS, group, module.getName(), module.getVersion(), "sources"));
                remap.setFieldsCsv(delayedFile(CSV_FIELD));
                remap.setMethodsCsv(delayedFile(CSV_METHOD));
                remap.setParamsCsv(delayedFile(CSV_PARAM));
                remap.dependsOn(TASK_EXTRACT_MAPPINGS);
                dummyTask.dependsOn(remap);
                break;
            }
        }
    }

    private Object getFile(String baseDir, String group, String name, String version, String classifier)
    {
        return delayedFile(
        baseDir + "/" + group.replace('.', '/') + "/" + name + "/" + version + "/" +
                name + "-" + version + (Strings.isNullOrEmpty(classifier) ? "" : "-" + classifier) + ".jar");
    }

    protected void doDepAtExtraction()
    {
        TaskExtractDepAts extract = makeTask(TASK_EXTRACT_DEP_ATS, TaskExtractDepAts.class);
        extract.addCollection("compile");
        extract.addCollection(CONFIG_PROVIDED);
        extract.addCollection(CONFIG_DEOBF_COMPILE);
        extract.addCollection(CONFIG_DEOBF_PROVIDED);
        extract.setOutputDir(delayedFile(DIR_DEP_ATS));
        extract.onlyIf(new Spec<Object>() {
            @Override
            public boolean isSatisfiedBy(Object arg0)
            {
                return getExtension().isUseDepAts();
            }
        });
        extract.doLast(new Action<Task>() {
            @Override public void execute(Task task)
            {
                DeobfuscateJar binDeobf = (DeobfuscateJar) task.getProject().getTasks().getByName(TASK_DEOBF_BIN);
                DeobfuscateJar decompDeobf = (DeobfuscateJar) task.getProject().getTasks().getByName(TASK_DEOBF);

                for (File file : task.getProject().fileTree(delayedFile(DIR_DEP_ATS)))
                {
                    binDeobf.addAt(file);
                    decompDeobf.addAt(file);
                }
            }
        });

        getExtension().atSource(delayedFile(DIR_DEP_ATS));
    }

    protected void configureRetromapping()
    {
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        Action<SourceSet> retromapCreator = new Action<SourceSet>() {
            @Override
            public void execute(final SourceSet set) {

                // native non-replaced
                DelayedFile rangeMap = delayedFile(getSourceSetFormatted(set, TMPL_RANGEMAP));
                DelayedFile retroMapped = delayedFile(getSourceSetFormatted(set, TMPL_RETROMAPED));

                final ExtractS2SRangeTask extractRangemap = makeTask(getSourceSetFormatted(set, TMPL_TASK_RANGEMAP), ExtractS2SRangeTask.class);
                extractRangemap.addSource(new File(project.getBuildDir(), "sources/main/java"));
                extractRangemap.setRangeMap(rangeMap);
                project.afterEvaluate(new Action<Project>() {
                    @Override
                    public void execute(Project project)
                    {
                        extractRangemap.addLibs(set.getCompileClasspath());
                    }
                });

                ApplyS2STask retromap = makeTask(getSourceSetFormatted(set, TMPL_TASK_RETROMAP), ApplyS2STask.class);
                retromap.addSource(set.getAllJava());
                retromap.setOut(retroMapped);
                retromap.addSrg(delayedFile(SRG_MCP_TO_SRG));
                retromap.addExc(delayedFile(EXC_MCP));
                retromap.addExc(delayedFile(EXC_SRG));
                retromap.setRangeMap(rangeMap);
                retromap.dependsOn(TASK_GENERATE_SRGS, extractRangemap);

                // TODO: add replacing extract task


                // for replaced sources
                rangeMap = delayedFile(getSourceSetFormatted(set, TMPL_RANGEMAP_RPL));
                retroMapped = delayedFile(getSourceSetFormatted(set, TMPL_RETROMAPED_RPL));
                File replacedSource = new File(project.getBuildDir(), "sources/"+set.getName()+"/java");

                final ExtractS2SRangeTask extractRangemap2 = makeTask(getSourceSetFormatted(set, TMPL_TASK_RANGEMAP_RPL), ExtractS2SRangeTask.class);
                extractRangemap2.addSource(replacedSource);
                extractRangemap2.setRangeMap(rangeMap);
                project.afterEvaluate(new Action<Project>() {
                    @Override
                    public void execute(Project project)
                    {
                        extractRangemap2.addLibs(set.getCompileClasspath());
                    }
                });
                extractRangemap2.dependsOn(getSourceSetFormatted(set, "source%sJava"));

                retromap = makeTask(getSourceSetFormatted(set, TMPL_TASK_RETROMAP_RPL), ApplyS2STask.class);
                retromap.addSource(replacedSource);
                retromap.setOut(retroMapped);
                retromap.addSrg(delayedFile(SRG_MCP_TO_SRG));
                retromap.addExc(delayedFile(EXC_MCP));
                retromap.addExc(delayedFile(EXC_SRG));
                retromap.setRangeMap(rangeMap);
                retromap.dependsOn(TASK_GENERATE_SRGS, extractRangemap2);
            }
        };

        // for existing sourceSets
        for (SourceSet set : javaConv.getSourceSets())
        {
            retromapCreator.execute(set);
        }
        // for user-defined ones
        javaConv.getSourceSets().whenObjectAdded(retromapCreator);

        final SourceSet main = javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

        // make retromapped sourcejar
        final Jar sourceJar = makeTask(TASK_SRC_JAR, Jar.class);
        final String retromappedSrc = getSourceSetFormatted(main, TMPL_RETROMAPED_RPL);
        sourceJar.from(main.getOutput().getResourcesDir());
        sourceJar.setClassifier("sources");
        sourceJar.dependsOn(main.getCompileJavaTaskName(), main.getProcessResourcesTaskName(), getSourceSetFormatted(main, TMPL_TASK_RETROMAP_RPL));

        sourceJar.from(new Closure<Object>(this, this) {
            public Object call() {
                File file = delayedFile(retromappedSrc).call();
                if (file.exists())
                    return sourceJar.getProject().zipTree(delayedFile(retromappedSrc));
                else
                    return new ArrayList<File>();
            }
        });

        // get scala sources too
        project.afterEvaluate(new Action<Project>()
        {
            @Override public void execute(Project project)
            {
                if (project.getPlugins().hasPlugin("scala"))
                {
                    ScalaSourceSet langSet = (ScalaSourceSet) new DslObject(main).getConvention().getPlugins().get("scala");
                    sourceJar.from(langSet.getAllScala());
                }
            }
        });
    }

    protected void makeRunTasks()
    {
        if (this.hasClientRun())
        {
            JavaExec exec = makeTask("runClient", JavaExec.class);
            exec.getOutputs().dir(delayedFile(REPLACE_RUN_DIR));
            exec.setMain(GRADLE_START_CLIENT);
            exec.workingDir(delayedFile(REPLACE_RUN_DIR));
            exec.setStandardOutput(System.out);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft client");

            exec.doFirst(makeRunDir);

            exec.dependsOn("makeStart");
        }

        if (this.hasServerRun())
        {
            JavaExec exec = makeTask("runServer", JavaExec.class);
            exec.getOutputs().dir(delayedFile(REPLACE_RUN_DIR));
            exec.setMain(GRADLE_START_SERVER);
            exec.workingDir(delayedFile(REPLACE_RUN_DIR));
            exec.setStandardOutput(System.out);
            exec.setStandardInput(System.in);
            exec.setErrorOutput(System.err);

            exec.setGroup("ForgeGradle");
            exec.setDescription("Runs the Minecraft Server");

            exec.doFirst(makeRunDir);

            exec.dependsOn("makeStart");
        }
    }

    protected final TaskDepDummy getDummyDep(String config, DelayedFile dummy, String taskName)
    {
        TaskDepDummy dummyTask = makeTask(taskName, TaskDepDummy.class);
        dummyTask.setOutputFile(dummy);

        ConfigurableFileCollection col = project.files(dummy);
        col.builtBy(dummyTask);

        project.getDependencies().add(config, col);

        return dummyTask;
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
            mappings.addMapping(priority, configs.getByName(CONFIG_DEOBF_COMPILE), Conf2ScopeMappingContainer.COMPILE);
            mappings.addMapping(priority, configs.getByName(CONFIG_DEOBF_PROVIDED), Conf2ScopeMappingContainer.PROVIDED);
        }
    }

    private static final Spec<File> AT_SPEC = new Spec<File>()
    {
        @Override
        public boolean isSatisfiedBy(File file)
        {
            return file.isFile() && file.getName().toLowerCase().endsWith("_at.cfg");
        }
    };

    protected void addAtsToDeobf()
    {
        // add src ATs
        DeobfuscateJar binDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN);
        DeobfuscateJar decompDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addAts(extAts);
        decompDeobf.addAts(extAts);

        // grab ATs from configured resource dirs
        boolean addedAts = getExtension().isUseDepAts();

        for (File at : getExtension().getResolvedAccessTransformerSources().filter(AT_SPEC).getFiles())
        {
            project.getLogger().lifecycle("Found AccessTransformer: {}", at.getName());
            binDeobf.addAt(at);
            decompDeobf.addAt(at);
            addedAts = true;
        }

        useLocalCache = useLocalCache || addedAts;
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
     * For run configurations. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty list for no arguments. NEVER NULL.
     */
    protected abstract List<String> getClientJvmArgs(T ext);

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
     * For run configurations. Is called late afterEvaluate or at runtime.
     * @param ext the Extension object
     * @return empty list for no arguments. NEVER NULL.
     */
    protected abstract List<String> getServerJvmArgs(T ext);

    /**
     * Configures the eclipse classpath
     * Also creates task that generate the eclipse run configs and attaches them to the eclipse task.
     */
    protected void configureEclipse()
    {
        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");
        eclipseConv.getClasspath().getPlusConfigurations().add(project.getConfigurations().getByName(CONFIG_MC));
        eclipseConv.getClasspath().getPlusConfigurations().add(project.getConfigurations().getByName(CONFIG_MC_DEPS));
        eclipseConv.getClasspath().getPlusConfigurations().add(project.getConfigurations().getByName(CONFIG_START));
        eclipseConv.getClasspath().getPlusConfigurations().add(project.getConfigurations().getByName(CONFIG_PROVIDED));

        if (this.hasClientRun())
        {
            GenEclipseRunTask eclipseClient = makeTask("makeEclipseCleanRunClient", GenEclipseRunTask.class);
            eclipseClient.setMainClass(GRADLE_START_CLIENT);
            eclipseClient.setProjectName(project.getName());
            eclipseClient.setOutputFile(project.file(project.getName() + "_Client.launch"));
            eclipseClient.setRunDir(delayedFile(REPLACE_RUN_DIR));
            eclipseClient.dependsOn(TASK_MAKE_START);
            eclipseClient.doFirst(makeRunDir);

            project.getTasks().getByName("eclipse").dependsOn(eclipseClient);
            project.getTasks().getByName("cleanEclipse").dependsOn("cleanMakeEclipseCleanRunClient");
        }

        if (this.hasServerRun())
        {
            GenEclipseRunTask eclipseServer = makeTask("makeEclipseCleanRunServer", GenEclipseRunTask.class);
            eclipseServer.setMainClass(GRADLE_START_SERVER);
            eclipseServer.setProjectName(project.getName());
            eclipseServer.setOutputFile(project.file(project.getName() + "_Server.launch"));
            eclipseServer.setRunDir(delayedFile(REPLACE_RUN_DIR));
            eclipseServer.dependsOn(TASK_MAKE_START);
            eclipseServer.doFirst(makeRunDir);

            project.getTasks().getByName("eclipse").dependsOn(eclipseServer);
            project.getTasks().getByName("cleanEclipse").dependsOn("cleanMakeEclipseCleanRunServer");
        }

        project.afterEvaluate(new Action<Project>() {

            @Override
            public void execute(Project project)
            {
                if (project.getState().getFailure() != null)
                    return;

                T ext = getExtension();
                if (hasClientRun())
                {
                    GenEclipseRunTask task = ((GenEclipseRunTask) project.getTasks().getByName("makeEclipseCleanRunClient"));
                    task.setArguments(Joiner.on(' ').join(getClientRunArgs(ext)));
                    task.setJvmArguments(Joiner.on(' ').join(getClientJvmArgs(ext)));
                }
                if (hasServerRun())
                {
                    GenEclipseRunTask task = ((GenEclipseRunTask) project.getTasks().getByName("makeEclipseCleanRunServer"));
                    task.setArguments(Joiner.on(' ').join(getServerRunArgs(ext)));
                    task.setJvmArguments(Joiner.on(' ').join(getServerJvmArgs(ext)));
                }
            }

        });

        // other dependencies
        project.getTasks().getByName("eclipseClasspath").dependsOn(TASK_DD_COMPILE, TASK_DD_PROVIDED);
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

        ideaConv.getModule().getScopes().get("COMPILE").get("plus").add(project.getConfigurations().getByName(CONFIG_MC_DEPS));
        ideaConv.getModule().getScopes().get("COMPILE").get("plus").add(project.getConfigurations().getByName(CONFIG_MC));
        ideaConv.getModule().getScopes().get("RUNTIME").get("plus").add(project.getConfigurations().getByName(CONFIG_START));
        // not provided here, becuase idea actually removes those from the runtime config
        ideaConv.getModule().getScopes().get("COMPILE").get("plus").add(project.getConfigurations().getByName(CONFIG_PROVIDED));

        // add deobf task dependencies
        project.getTasks().getByName("ideaModule").dependsOn(TASK_DD_COMPILE, TASK_DD_PROVIDED).doFirst(makeRunDir);

        // fix the idea bug
        ideaConv.getModule().setInheritOutputDirs(true);

        Task task = makeTask("genIntellijRuns", DefaultTask.class);
        task.doFirst(makeRunDir);
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
        task.setDescription("Generates the ForgeGradle run configurations for intellij Idea");

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
                        Joiner.on(' ').join(getClientRunArgs(ext)),
                        Joiner.on(' ').join(getClientJvmArgs(ext))
                } : null,

                this.hasServerRun() ? new String[]
                {
                        "Minecraft Server",
                        GRADLE_START_SERVER,
                        Joiner.on(' ').join(getServerRunArgs(ext)),
                        Joiner.on(' ').join(getServerJvmArgs(ext))
                } : null
        };

        for (String[] data : config)
        {
            if (data == null)
                continue;

            Element child = addXml(root, "configuration", ImmutableMap.of(
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
            addXml(child, "option", ImmutableMap.of("name", "VM_PARAMETERS", "value", data[3]));
            addXml(child, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", data[2]));
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

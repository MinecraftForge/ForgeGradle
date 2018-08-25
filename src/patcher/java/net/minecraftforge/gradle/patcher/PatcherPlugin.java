package net.minecraftforge.gradle.patcher;

import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCMetaTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.patcher.task.TaskGenerateUserdevConfig;
import net.minecraftforge.gradle.patcher.task.TaskApplyMappings;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskCreateExc;
import net.minecraftforge.gradle.patcher.task.TaskCreateSrg;
import net.minecraftforge.gradle.patcher.task.TaskDownloadAssets;
import net.minecraftforge.gradle.patcher.task.TaskExtractMCPData;
import net.minecraftforge.gradle.patcher.task.TaskExtractNatives;
import net.minecraftforge.gradle.patcher.task.TaskExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskFilterNewJar;
import net.minecraftforge.gradle.patcher.task.TaskGenerateBinPatches;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import net.minecraftforge.gradle.patcher.task.TaskReobfuscateJar;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.xml.sax.SAXException;

import com.google.common.collect.Lists;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatcherPlugin implements Plugin<Project> {
    private static final String MC_DEP_CONFIG = "compile";

    @Override
    public void apply(@Nonnull Project project) {
        final PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final JavaPluginConvention javaConv = (JavaPluginConvention)project.getConvention().getPlugins().get("java");
        final File natives_folder = project.file("build/natives/");

        Jar jarConfig = (Jar)project.getTasks().getByName("jar");

        TaskProvider<DownloadMCPMappingsTask> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<DownloadMCMetaTask> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMetaTask.class);
        TaskProvider<TaskExtractNatives> extractNatives = project.getTasks().register("extractNatives", TaskExtractNatives.class);
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<TaskApplyMappings> toMCPConfig = project.getTasks().register("srg2mcp", TaskApplyMappings.class);
        TaskProvider<Copy> extractMapped = project.getTasks().register("extractMapped", Copy.class);
        TaskProvider<TaskCreateSrg> createMcp2Srg = project.getTasks().register("createMcp2Srg", TaskCreateSrg.class);
        TaskProvider<TaskCreateSrg> createMcp2Obf = project.getTasks().register("createMcp2Obf", TaskCreateSrg.class);
        TaskProvider<TaskCreateExc> createExc = project.getTasks().register("createExc", TaskCreateExc.class);
        TaskProvider<TaskExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", TaskExtractRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", TaskApplyRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeBaseConfig = project.getTasks().register("applyRangeMapBase", TaskApplyRangeMap.class);
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);
        TaskProvider<TaskDownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", TaskDownloadAssets.class);
        TaskProvider<TaskReobfuscateJar> reobfJar = project.getTasks().register("reobfJar", TaskReobfuscateJar.class);
        TaskProvider<TaskGenerateBinPatches> genJoinedBinPatches = project.getTasks().register("genJoinedBinPatches", TaskGenerateBinPatches.class);
        TaskProvider<TaskGenerateBinPatches> genClientBinPatches = project.getTasks().register("genClientBinPatches", TaskGenerateBinPatches.class);
        TaskProvider<TaskGenerateBinPatches> genServerBinPatches = project.getTasks().register("genServerBinPatches", TaskGenerateBinPatches.class);
        TaskProvider<DefaultTask> genBinPatches = project.getTasks().register("genBinPatches", DefaultTask.class);
        TaskProvider<TaskFilterNewJar> filterNew = project.getTasks().register("filterJarNew", TaskFilterNewJar.class);
        TaskProvider<Jar> sourcesJar = project.getTasks().register("sourcesJar", Jar.class);
        TaskProvider<Jar> universalJar = project.getTasks().register("universalJar", Jar.class);
        TaskProvider<Jar> userdevJar = project.getTasks().register("userdevJar", Jar.class);
        TaskProvider<TaskGenerateUserdevConfig> userdevConfig = project.getTasks().register("userdevConfig", TaskGenerateUserdevConfig.class);
        TaskProvider<DefaultTask> release = project.getTasks().register("release", DefaultTask.class);

        release.configure(task -> {
            task.dependsOn(sourcesJar, universalJar, userdevJar);
        });
        dlMappingsConfig.configure(task -> {
            task.setMappings(extension.getMappings());
        });
        dlMCMetaConfig.configure(task -> {
            task.setMcVersion(extension.mcVersion);
        });
        extractNatives.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.setMeta(dlMCMetaConfig.get().getOutput());
            task.setOutput(natives_folder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.setMeta(dlMCMetaConfig.get().getOutput());
        });
        applyConfig.configure(task -> {
            task.setPatches(extension.patches);
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyConfig);
            task.setInput(applyConfig.get().getOutput());
            task.setMappings(dlMappingsConfig.get().getOutput());
        });
        extractMapped.configure(task -> {
            task.dependsOn(toMCPConfig);
            task.from(project.zipTree(toMCPConfig.get().getOutput()));
            task.into(extension.patchedSrc);
        });
        extractRangeConfig.configure(task -> {
            task.setOnlyIf(t -> extension.patches != null);
            task.addDependencies(project.getConfigurations().getByName(MC_DEP_CONFIG));
            task.addDependencies(jarConfig.getArchivePath());
        });

        createMcp2Srg.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
            task.toSrg();
        });
        createMcp2Obf.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
            task.toNotch();
        });
        createExc.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
        });

        applyRangeConfig.configure(task -> {
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
            //TODO: Extra SRG/EXCs
        });
        applyRangeBaseConfig.configure(task -> {
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
            //TODO: Extra SRG/EXCs
        });
        genConfig.configure(task -> {
            task.dependsOn(applyRangeBaseConfig);
            task.setOnlyIf(t -> extension.patches != null);
            task.setModified(applyRangeBaseConfig.get().getOutput());
            task.setPatches(extension.patches);
        });

        reobfJar.configure(task -> {
            task.dependsOn(jarConfig, dlMappingsConfig, createMcp2Obf);
            task.setInput(jarConfig.getArchivePath());
            task.setSrg(createMcp2Obf.get().getOutput());
            task.setClasspath(project.getConfigurations().getByName(MC_DEP_CONFIG));
            //TODO: Extra SRGs
        });
        genJoinedBinPatches.configure(task -> {
            task.dependsOn(reobfJar, createMcp2Obf);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSrg(createMcp2Obf.get().getOutput());
            task.setSide("joined");
        });
        genClientBinPatches.configure(task -> {
            task.dependsOn(reobfJar, createMcp2Obf);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSrg(createMcp2Obf.get().getOutput());
            task.setSide("client");
        });
        genServerBinPatches.configure(task -> {
            task.dependsOn(reobfJar, createMcp2Obf);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSrg(createMcp2Obf.get().getOutput());
            task.setSide("server");
        });
        genBinPatches.configure(task -> {
            task.dependsOn(genJoinedBinPatches.get(), genClientBinPatches.get(), genServerBinPatches.get());
        });
        filterNew.configure(task -> {
            task.dependsOn(reobfJar, createMcp2Obf);
            task.setInput(reobfJar.get().getOutput());
            task.setSrg(createMcp2Obf.get().getOutput());
        });
        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        sourcesJar.configure(task -> {
            task.dependsOn(genConfig, applyRangeConfig);
            task.from(project.zipTree(applyRangeConfig.get().getOutput()));
            task.setClassifier("sources");
        });
        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         * client.lzma
         * server.lzma
         */
        universalJar.configure(task -> {
            task.dependsOn(filterNew, genClientBinPatches, genServerBinPatches);
            task.from(project.zipTree(filterNew.get().getOutput()));
            task.from(genClientBinPatches.get().getOutput());
            task.from(genServerBinPatches.get().getOutput());
            task.from(javaConv.getSourceSets().getByName("main").getResources());
            task.setClassifier("universal");
        });
        /*UserDev:
         * config.json
         * joined.lzma
         * sources.jar
         * universal.jar
         * patches/
         *   net/minecraft/item/Item.java.patch
         * ats/
         *   at1.cfg
         *   at2.cfg
         */
        userdevJar.configure(task -> {
            task.dependsOn(userdevConfig, genJoinedBinPatches, sourcesJar, universalJar, genConfig);
            task.from(userdevConfig.get().getOutput(), e -> {e.rename(f -> "config.json"); });
            task.from(genJoinedBinPatches.get().getOutput(), e -> { e.rename(f -> "joined.lzma"); });
            task.from(sourcesJar.get().getArchivePath(), e-> {e.rename(f -> "sources.jar"); });
            task.from(universalJar.get().getArchivePath(), e-> {e.rename(f -> "universal.jar"); });
            task.from(genConfig.get().getPatches(), e -> { e.into("patches/"); });
            task.setClassifier("userdev");
        });

        project.afterEvaluate(p -> {

            //Add Known repos
            project.getRepositories().maven(e -> {
                e.setUrl("https://libraries.minecraft.net/");
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().maven(e -> {
                e.setUrl("http://files.minecraftforge.net/maven/");
            });

            //Add PatchedSrc to a main sourceset and build range tasks
            SourceSet mainSource = javaConv.getSourceSets().getByName("main");
            applyRangeConfig.get().setSources(mainSource.getJava().getSrcDirs());
            applyRangeBaseConfig.get().setSources(extension.patchedSrc);
            mainSource.java(v -> { v.srcDir(extension.patchedSrc); });
            mainSource.resources(v -> { }); //TODO: Asset downloading, needs asset index from json.
            javaConv.getSourceSets().stream().forEach(s -> extractRangeConfig.get().addSources(s.getJava().getSrcDirs()));

            if (extension.patches != null && !extension.patches.exists()) { //Auto-make folders so that gradle doesnt explode some tasks.
                extension.patches.mkdirs();
            }

            if (extension.parent != null) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                TaskContainer tasks = extension.parent.getTasks();
                MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (mcp != null) {
                    SetupMCPTask setupMCP = (SetupMCPTask)tasks.getByName("setupMCP");

                    if (extension.cleanSrc == null) {
                        extension.cleanSrc = setupMCP.getOutput();
                        applyConfig.get().dependsOn(tasks.getByName("setupMCP"));
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
                    }

                    File mcpConfig = ((DownloadMCPConfigTask)tasks.getByName("downloadConfig")).getOutput();

                    if (createMcp2Srg.get().getSrg() == null) { //TODO: Make extractMCPData macro
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractSrg", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        createMcp2Srg.get().setSrg(ext.get().getOutput());
                        createMcp2Srg.get().dependsOn(ext);
                    }

                    if (createMcp2Obf.get().getSrg() == null) {
                        createMcp2Obf.get().setSrg(createMcp2Srg.get().getSrg());
                    }

                    if (createExc.get().getSrg() == null) {
                        createExc.get().setSrg(createMcp2Srg.get().getSrg());
                        createExc.get().dependsOn(createMcp2Srg);
                    }

                    if (createExc.get().getStatics() == null) {
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractStatic", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        ext.get().setKey("statics");
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setStatics(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }

                    if (createExc.get().getConstructors() == null) {
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractConstructors", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        ext.get().setKey("constructors");
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setConstructors(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }

                    genJoinedBinPatches.get().setCleanJar(setupMCP.getJoinedJar());
                    genJoinedBinPatches.get().dependsOn(setupMCP);
                    genClientBinPatches.get().setCleanJar(setupMCP.getClientJar());
                    genClientBinPatches.get().dependsOn(setupMCP);
                    genServerBinPatches.get().setCleanJar(setupMCP.getServerJar());
                    genServerBinPatches.get().dependsOn(setupMCP);
                    filterNew.get().dependsOn(setupMCP);
                    filterNew.get().addBlacklist(setupMCP.getJoinedJar());

                } else if (patcher != null) {
                    PatcherExtension pExt = extension.parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    if (dlMappingsConfig.get().getMappings() == null) {
                        dlMappingsConfig.get().setMappings(extension.getMappings());
                    }

                    if (extension.cleanSrc == null) {
                        TaskApplyPatches task = (TaskApplyPatches)tasks.getByName(applyConfig.get().getName());
                        extension.cleanSrc = task.getOutput();
                        applyConfig.get().dependsOn(task);
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
                    }

                    if (createMcp2Srg.get().getSrg() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createMcp2Srg.get().setSrg(extract.getOutput());
                            createMcp2Srg.get().dependsOn(extract);
                        } else {
                            TaskCreateSrg task = (TaskCreateSrg)tasks.getByName(createMcp2Srg.get().getName());
                            createMcp2Srg.get().setSrg(task.getSrg());
                            createMcp2Srg.get().dependsOn(task);
                        }
                    }

                    if (createMcp2Obf.get().getSrg() == null) {
                        createMcp2Obf.get().setSrg(createMcp2Srg.get().getSrg());
                        createMcp2Obf.get().dependsOn(createMcp2Srg.get());
                    }

                    if (createExc.get().getSrg() == null) { //TODO: Make a macro for Srg/Static/Constructors
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createExc.get().setSrg(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateSrg task = (TaskCreateSrg)tasks.getByName(createExc.get().getName());
                            createExc.get().setSrg(task.getSrg());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getStatics() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractStatic"));
                        if (extract != null) {
                            createExc.get().setStatics(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateExc task = (TaskCreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setStatics(task.getStatics());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getConstructors() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractConstructors"));
                        if (extract != null) {
                            createExc.get().setConstructors(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateExc task = (TaskCreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setConstructors(task.getConstructors());
                            createExc.get().dependsOn(task);
                        }
                    }
                    for (TaskProvider<TaskGenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                        TaskGenerateBinPatches pgen = (TaskGenerateBinPatches)tasks.getByName(task.get().getName());
                        task.get().dependsOn(pgen.getDependsOn());
                        task.get().setCleanJar(pgen.getCleanJar());
                        for (File patches : pgen.getPatchSets()) {
                            task.get().addPatchSet(patches);
                        }
                    }

                    filterNew.get().dependsOn(tasks.getByName("jar"));
                    filterNew.get().addBlacklist(((Jar)tasks.getByName("jar")).getArchivePath());
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }
            }
            MinecraftRepo.attach(project);
            project.getDependencies().add(MC_DEP_CONFIG, "net.minecraft:client:" + extension.mcVersion + ":extra");

            if (!extension.getAccessTransformers().isEmpty()) {
                Project mcp = getMcpParent(project);
                if (mcp == null) {
                    throw new IllegalStateException("AccessTransformers specified, with no MCP Parent");
                }
                SetupMCPTask setupMCP = (SetupMCPTask)mcp.getTasks().getByName("setupMCP");
                setupMCP.addPreDecompile(project.getName() + "AccessTransformer", new AccessTransformerFunction(mcp, extension.getAccessTransformers()));
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("ats/"));
                    userdevConfig.get().addAT(f);
                });
            }

            if (!extension.getExtraMappings().isEmpty()) {
                extension.getExtraMappings().stream().filter(e -> e instanceof File).map(e -> (File)e).forEach(e -> {
                    userdevJar.get().from(e, c -> c.into("srgs/"));
                    userdevConfig.get().addSRG(e);
                });
                extension.getExtraMappings().stream().filter(e -> e instanceof String).map(e -> (String)e).forEach(e -> userdevConfig.get().addSRGLine(e));
            }

            //Make sure tasks that require a valid classpath happen after making the classpath
            p.getTasks().withType(GenerateEclipseClasspath.class, t -> { t.dependsOn(extractNatives.get(), downloadAssets.get()); });
            //TODO: IntelliJ plugin?

            doEclipseFixes(project, natives_folder);
        });
    }

    @SuppressWarnings("unchecked")
    private void doEclipseFixes(Project project, File natives) {
        final String LIB_ATTR = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";
        project.getTasks().withType(GenerateEclipseClasspath.class, task -> {
            task.doFirst(t -> {
                task.getClasspath().getSourceSets().forEach(s -> {
                    if (s.getName().equals("main")) { //Eclipse requires main to exist.. or it gets wonkey
                        s.getAllSource().getSrcDirs().stream().filter(f -> !f.exists()).forEach(File::mkdirs);
                    }
                });
            });
            task.doLast(t -> {
                try {
                    Node xml = new XmlParser().parse(task.getOutputFile());

                    List<Node> entries = (ArrayList<Node>)xml.get("classpathentry");
                    Set<String> paths = new HashSet<>();
                    List<Node> remove = new ArrayList<>();
                    entries.stream().filter(e -> "src".equals(e.get("@kind"))).forEach(e -> {
                        if (!paths.add((String)e.get("@path"))) { //Eclipse likes to duplicate things... No idea why, lets kill them off
                            remove.add(e);
                        }
                        if (((List<Node>)e.get("attributes")).isEmpty()) {
                            e.appendNode("attributes");
                        }
                        Node attr = ((List<Node>)e.get("attributes")).get(0);
                        if (((List<Node>)attr.get("attribute")).stream().noneMatch(n -> LIB_ATTR.equals(n.get("@name")))) {
                            attr.appendNode("attribute", props("name", LIB_ATTR, "value", natives.getAbsolutePath()));
                        }
                    });
                    remove.forEach(xml::remove);
                    try (OutputStream fos = new FileOutputStream(task.getOutputFile())) {
                        IOUtils.write(XmlUtil.serialize(xml), fos, StandardCharsets.UTF_8);
                    }

                    File run_dir = project.file("run");
                    if (!run_dir.exists()) {
                        run_dir.mkdirs();
                    }

                    String niceName = project.getName().substring(0, 1).toUpperCase() + project.getName().substring(1);
                    for (boolean client : new boolean[] {true, false}) {
                        xml = new Node(null, "launchConfiguration", props("type", "org.eclipse.jdt.launching.localJavaApplication"));
                        xml.appendNode("stringAttribute", props("key", "org.eclipse.jdt.launching.MAIN_TYPE", "value", client ? "mcp.client.Start" : "net.minecraft.server.MinecraftServer"));
                        xml.appendNode("stringAttribute", props("key", "org.eclipse.jdt.launching.PROJECT_ATTR", "value", project.getName()));
                        xml.appendNode("stringAttribute", props("key", "org.eclipse.jdt.launching.WORKING_DIRECTORY", "value", run_dir.getAbsolutePath()));
                        Node env = xml.appendNode("mapAttribute", props("key", "org.eclipse.debug.core.environmentVariables"));
                        env.appendNode("mapEntry", props("key", "assetDirectory", "value", Utils.getCache(project, "assets/").getAbsolutePath()));

                        try (OutputStream fos = new FileOutputStream(project.file(client ? "RunClient" + niceName +".launch" : "RunServer" + niceName +".launch"))) {
                            IOUtils.write(XmlUtil.serialize(xml), fos, StandardCharsets.UTF_8);
                        }
                    }
                } catch (IOException | SAXException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private Map<String, String> props(String... data) {
        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("Properties must be key,value pairs");
        }
        Map<String, String> ret = new HashMap<>();
        for (int x = 0; x < data.length; x += 2) {
            ret.put(data[x], data[x + 1]);
        }
        return ret;
    }

    private Project getMcpParent(Project project) {
        final PatcherExtension extension = project.getExtensions().findByType(PatcherExtension.class);
        if (extension == null || extension.parent == null) {
            return null;
        }
        MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
        PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);
        if (mcp != null) {
            return extension.parent;
        } else if (patcher != null) {
            return getMcpParent(extension.parent);
        }
        return null;
    }
}

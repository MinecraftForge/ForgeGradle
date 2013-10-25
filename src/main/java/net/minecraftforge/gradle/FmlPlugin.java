package net.minecraftforge.gradle;

import static net.minecraftforge.gradle.Constants.DEOBF_DATA;
import static net.minecraftforge.gradle.Constants.ECLIPSE_CLEAN;
import static net.minecraftforge.gradle.Constants.ECLIPSE_FML;
import static net.minecraftforge.gradle.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.Constants.JAR_SRG;
import static net.minecraftforge.gradle.Constants.MC_JAR_URL;
import static net.minecraftforge.gradle.Constants.MC_SERVER_URL;
import static net.minecraftforge.gradle.Constants.PACKAGED_EXC;
import static net.minecraftforge.gradle.Constants.PACKAGED_SRG;
import static net.minecraftforge.gradle.Constants.PATCH_DIR;
import static net.minecraftforge.gradle.Constants.WORKSPACE;
import static net.minecraftforge.gradle.Constants.ZIP_DECOMP;
import static net.minecraftforge.gradle.Constants.ZIP_FML;
import static net.minecraftforge.gradle.FmlConstants.ASTYLE_CFG;
import static net.minecraftforge.gradle.FmlConstants.CHANGELOG;
import static net.minecraftforge.gradle.FmlConstants.FML_CLIENT;
import static net.minecraftforge.gradle.FmlConstants.FML_COMMON;
import static net.minecraftforge.gradle.FmlConstants.FML_ECLIPSE_WS;
import static net.minecraftforge.gradle.FmlConstants.FML_PATCH_DIR;
import static net.minecraftforge.gradle.FmlConstants.JOINED_EXC;
import static net.minecraftforge.gradle.FmlConstants.JOINED_SRG;
import static net.minecraftforge.gradle.FmlConstants.JSON_DEV;
import static net.minecraftforge.gradle.FmlConstants.JSON_REL;
import static net.minecraftforge.gradle.FmlConstants.MCP_PATCH;
import static net.minecraftforge.gradle.FmlConstants.MERGE_CFG;
import static net.minecraftforge.gradle.FmlConstants.PACKAGED_PATCH;
import static net.minecraftforge.gradle.FmlConstants.PACK_CSV;
//import edu.sc.seis.launch4j.Launch4jPluginExtension;
import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.HashMap;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedString.IDelayedResolver;
import net.minecraftforge.gradle.tasks.ChangelogTask;
import net.minecraftforge.gradle.tasks.CompressLZMA;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.DelayedJar;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.ExtractTask;
import net.minecraftforge.gradle.tasks.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.GeneratePatches;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.MergeMappingsTask;
import net.minecraftforge.gradle.tasks.ObfuscateTask;
import net.minecraftforge.gradle.tasks.ObtainMcpStuffTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.ProjectTask;
import net.minecraftforge.gradle.tasks.SubprojectTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.process.ExecSpec;
import org.gradle.testfixtures.ProjectBuilder;

import argo.jdom.JsonNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class FmlPlugin implements Plugin<Project>, IDelayedResolver
{
    public Project project;
    @SuppressWarnings("serial")
    private Closure<Boolean> CALL_FALSE = new Closure<Boolean>(null){ public Boolean call(Object o){ return false; }};

    @Override
    public void apply(Project project)
    {
        // set project
        this.project = project;
        
//        project.getBuildscript().getRepositories().add(project.getBuildscript().getRepositories().mavenCentral());
//        project.getBuildscript().getDependencies().add("classpath", "edu.sc.seis.gradle:launch4j:latest.integration");
        
        //applyPlugin("launch4j");

        //project.getPlugins().apply(BasePlugin.class); //Apply the base plugin, for archive configs
        // apply extension         object
        project.getExtensions().create(Constants.EXT_NAME, ExtensionObject.class, project);
        
        //configureLaunch4J();

        obtainingTasks();
        mappingFixTask();
        jarProcessTasks();
        otherFmlTasks();
        sourceCopyTasks();
        packageTasks();

        // the master setup task.
        Task task = makeTask("setupFML", DefaultTask.class);
        task.dependsOn("extractFmlSources", "generateProjects", "eclipse");
        task.setGroup("FML");
        
        // the master task.
        task = makeTask("buildPackages", DefaultTask.class);
        task.dependsOn("launch4j", "packageUniversal", "createChangelog", "packageInstaller");
        task.setGroup("FML");
    }
    
//    private void configureLaunch4J()
//    {
//        
//        Task task = project.getTasks().getByName("generateXmlConfig");
//        task.dependsOn("packageInstaller");
//        task.getInputs().file(delayedFile(Constants.INSTALLER));
//        task.doFirst(new Closure(project, this) {
//            @Override
//            public Object call()
//            {
//                // get teh extension object
//                Launch4jPluginExtension ext = (Launch4jPluginExtension) project.getExtensions().getByName("launch4j");
//                //ext.setJar(((Zip)project.getTasks().getByName("packageInstaller")).getArchivePath().getAbsolutePath());
//                //ext.setOutfile(((Zip)project.getTasks().getByName("packageInstaller")).getArchiveName().replace(".zip", ".exe"));
//                
//                try
//                {
//                    // set jar stuff
//                    JarFile file = new JarFile(delayedFile(Constants.INSTALLER).call());
//                    java.util.jar.Manifest man = file.getManifest();
//                    ext.setMainClassName(man.getMainAttributes().getValue("Main-Class"));
//                }
//                catch (IOException e)
//                {
//                    Throwables.propagate(e); // -_-
//                }
//                
//                return null;
//            }
//            
//            @Override
//            public Object call(Object obj)
//            {
//                return call();
//            }
//            
//            @Override
//            public Object call(Object... obj)
//            {
//                return call();
//            }
//        });
//    }

    @SuppressWarnings({ "serial", "rawtypes" })
    private void obtainingTasks()
    {
        DownloadTask task;

        task = makeTask("downloadClient", DownloadTask.class);
        task.setOutput(delayedFile(JAR_CLIENT_FRESH));
        task.setUrl(delayedString(MC_JAR_URL));

        task = makeTask("downloadServer", DownloadTask.class);
        task.setOutput(delayedFile(JAR_SERVER_FRESH));
        task.setUrl(delayedString(MC_SERVER_URL));
        
        task = makeTask("downloadBaseInstaller", DownloadTask.class);
        task.setOutput(delayedFile(Constants.INSTALLER));
        task.setUrl(delayedString(Constants.INSTALLER_URL));
        
        ObtainMcpStuffTask mcpTask = makeTask("downloadMcpTools", ObtainMcpStuffTask.class);
        mcpTask.setMcpUrl(delayedString(Constants.MCP_URL));
        mcpTask.setFfJar(delayedFile(Constants.FERNFLOWER));
        mcpTask.setInjectorJar(delayedFile(Constants.EXCEPTOR));

        //read json, and download the natives
        project.afterEvaluate(new Closure(project, this) {
            @Override
            public Object call()
            {
                try
                {
                    Copy copyTask = makeTask("extractNatives", Copy.class);
                    copyTask.exclude("META-INF", "META-INF/**", "META-INF/*");
                    copyTask.into(Constants.ECLIPSE_RUN + "/bin/natives");
                    copyTask.dependsOn("extractWorkspace");

                    // parse json
                    JsonNode node = null;
                    node = Constants.PARSER.parse(Files.newReader(delayedFile(FmlConstants.JSON_DEV).call().getAbsoluteFile(), Charset.defaultCharset()));
                    // itterate through the libs
                    for (JsonNode lib : node.getArrayNode("libraries"))
                    {
                        if (lib.isNode("natives") && lib.isNode("extract"))
                        {
                            String notation = lib.getStringValue("name");
                            // build the path from the
                            StringBuilder path = new StringBuilder();
                            // 0 = group 1 = artifact 2 = version
                            String[] split = notation.split(":");
                            path.append(split[0].replace('.', '/'));
                            path.append('/').append(split[1]).append('/').append(split[2]).append('/');
                            path.append(split[1]).append('-').append(split[2]).append("-natives-").append(Constants.OPERATING_SYSTEM.name().toLowerCase()).append(".jar");
                            String outpath = path.toString();
                            //make the download task.
                            DownloadTask task = makeTask("downloadNatives-" + split[1], DownloadTask.class);
                            task.setOutput(delayedFile("{CACHE_DIR}/" + outpath));
                            task.setUrl(delayedString("http://repo1.maven.org/maven2/" + outpath));
                            // add it to the copy task
                            copyTask.from(delayedZipTree("{CACHE_DIR}/" + outpath));
                            copyTask.dependsOn("downloadNatives-" + split[1]);
                        }
                    }

                }
                catch (Exception e)
                {
                    Throwables.propagate(e);  // throw it.
                }
                
                return null;
            }
            
            @Override
            public Object call(Object obj)
            {
                return call();
            }
            
            @Override
            public Object call(Object... obj)
            {
                return call();
            }
        });
    }

    /**
     * Fixes the SRG, EXC and MCP patch files to use the package refractor.
     */
    private void mappingFixTask()
    {
        MergeMappingsTask task = makeTask("fixMappings", MergeMappingsTask.class);
        {
            task.setPackageCSV(delayedFile(PACK_CSV));
            task.setInSRG(delayedFile(JOINED_SRG));
            task.setInEXC(delayedFile(JOINED_EXC));
            task.setOutSRG(delayedFile(PACKAGED_SRG));
            task.setOutEXC(delayedFile(PACKAGED_EXC));
            task.setInPatch(delayedFile(MCP_PATCH));
            task.setOutPatch(delayedFile(PACKAGED_PATCH));
        }
    }

    private void jarProcessTasks()
    {
        MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
        {
            task.setClient(delayedFile(JAR_CLIENT_FRESH));
            task.setServer(delayedFile(JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(JAR_MERGED));
            task.setMergeCfg(delayedFile(MERGE_CFG));

            // task dependencies
            task.dependsOn("downloadClient", "downloadServer");
        }

        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            // configure task variables
            task2.setInJar(delayedFile(JAR_MERGED));
            task2.setExceptorJar(delayedFile(EXCEPTOR));
            task2.setOutJar(delayedFile(JAR_SRG));
            task2.setSrg(delayedFile(PACKAGED_SRG));
            task2.setExceptorCfg(delayedFile(PACKAGED_EXC));
            task2.addTransformer(delayedFile(FML_COMMON + "/fml_at.cfg"));

            // task dependencies
            task2.dependsOn("downloadMcpTools", "fixMappings", "mergeJars");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(JAR_SRG));
            task3.setOutJar(delayedFile(ZIP_DECOMP));
            task3.setFernFlower(delayedFile(FERNFLOWER));
            task3.setPatch(delayedFile(PACKAGED_PATCH));
            task3.setAstyleConfig(delayedFile(ASTYLE_CFG));

            // task dependencies
            task3.dependsOn("downloadMcpTools", "deobfuscateJar", "fixMappings");
        }

        PatchJarTask task4 = makeTask("doFmlStuff", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_DECOMP));
            task4.setOutJar(delayedFile(ZIP_FML));
            task4.setInPatches(delayedFile(FML_PATCH_DIR));

            // task dependencies
            task4.dependsOn("decompile");
        }
    }

    private void sourceCopyTasks()
    {
        // extract eclipse workspace
        ExtractTask extractTask = makeTask("extractWorkspace", ExtractTask.class);
        extractTask.from(delayedFile(FML_ECLIPSE_WS));
        extractTask.into(delayedFile(WORKSPACE));

        // extract MC Resources
        extractTask = makeTask("extractMcResources", ExtractTask.class);
        extractTask.exclude("**.java", "*.java", "**/*.java");
        extractTask.setIncludeEmptyDirs(false);
        extractTask.from(delayedFile(ZIP_DECOMP));
        extractTask.into(delayedFile(ECLIPSE_CLEAN + "/src/main/resources"));
        extractTask.dependsOn("extractWorkspace", "decompile");

        // copy Start.java
        Copy copyTask = makeTask("copyStart", Copy.class);
        copyTask.from(delayedFile("{MAPPINGS_DIR}/patches"));
        copyTask.include("Start.java");
        copyTask.into(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
        copyTask.dependsOn("extractMcResources");

        // extract MC sources
        extractTask = makeTask("extractMcSource", ExtractTask.class);
        extractTask.include("**.java", "*.java", "**/*.java");
        extractTask.setIncludeEmptyDirs(false);
        extractTask.from(delayedFile(ZIP_DECOMP));
        extractTask.into(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
        extractTask.dependsOn("copyStart");

        // extract FML Resources
        extractTask = makeTask("extractFmlResources", ExtractTask.class);
        extractTask.exclude("**.java", "*.java", "**/*.java");
        extractTask.from(delayedFile(ZIP_FML));
        extractTask.into(delayedFile(ECLIPSE_FML + "/src/resources"));
        extractTask.dependsOn("doFmlStuff", "extractWorkspace");

        // copy DEOBF data
        copyTask = makeTask("copyDeobfData", Copy.class);
        copyTask.from(delayedFile(DEOBF_DATA));
        copyTask.into(delayedFile(ECLIPSE_FML + "/src/resources"));
        copyTask.dependsOn("extractFmlResources", "compressDeobfData");

        // extract FML sources
        extractTask = makeTask("extractFmlSources", ExtractTask.class);
        extractTask.include("**.java", "*.java", "**/*.java");
        extractTask.exclude("cpw/**");
        extractTask.from(delayedFile(ZIP_FML));
        extractTask.into(delayedFile(ECLIPSE_FML + "/src/minecraft"));
        extractTask.dependsOn("copyDeobfData");

    }

    private void otherFmlTasks()
    {
        ProjectTask projecter = makeTask("generateProjectClean", ProjectTask.class);
        projecter.setTargetDir(delayedFile(ECLIPSE_CLEAN));
        projecter.setJson(delayedFile(JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
        projecter.dependsOn("extractNatives");

        projecter = makeTask("generateProjectFML", ProjectTask.class);
        projecter.setJson(delayedFile(JSON_DEV));
        projecter.setTargetDir(delayedFile(ECLIPSE_FML));
        projecter.addSource(delayedFile(ECLIPSE_FML + "/src/minecraft")); // Minecraft's base files
        projecter.addResource(delayedFile(ECLIPSE_FML + "/src/resources"));
        projecter.addSource(delayedFile(FML_CLIENT)); // Eventually merge this into a single 'fml_source' in the repository
        projecter.addSource(delayedFile(FML_COMMON));
        projecter.addResource(delayedFile(FML_CLIENT)); // Eventually change to 'fml_resources' in the repo
        projecter.addResource(delayedFile(FML_COMMON));
        projecter.dependsOn("extractNatives");

        makeTask("generateProjects", DefaultTask.class).dependsOn("generateProjectClean", "generateProjectFML");

        CompressLZMA compressTask = makeTask("compressDeobfData", CompressLZMA.class);
        compressTask.setInputFile(delayedFile(PACKAGED_SRG));
        compressTask.setOutputFile(delayedFile(DEOBF_DATA));
        compressTask.dependsOn("fixMappings");

        GeneratePatches genPatcher = makeTask("genPatches", GeneratePatches.class);
        genPatcher.setPatchDir(delayedFile(PATCH_DIR));
        genPatcher.setOriginalDir(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
        genPatcher.setChangedDir(delayedFile(ECLIPSE_FML + "/src/minecraft"));
        genPatcher.setOriginalPrefix("../src-base/minecraft");
        genPatcher.setChangedPrefix("../src-work/minecraft");
        genPatcher.setGroup("FML");

        SubprojectTask eclipseClean = makeTask("eclipseClean", SubprojectTask.class);
        eclipseClean.setBuildFile(delayedFile(ECLIPSE_CLEAN + "/build.gradle"));
        eclipseClean.setTasks("eclipse");
        eclipseClean.dependsOn("extractMcSource", "generateProjects");

        SubprojectTask eclipseFML = makeTask("eclipseFML", SubprojectTask.class);
        eclipseFML.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
        eclipseFML.setTasks("eclipse");
        eclipseFML.dependsOn("extractFmlSources", "generateProjects");

        Task eclipse = makeTask("eclipse", DefaultTask.class);
        eclipse.dependsOn("eclipseClean", "eclipseFML");

        Delete clean = makeTask("cleanFml", Delete.class);
        clean.delete("eclipse");
        clean.setGroup("Clean");

        ObfuscateTask obfTask = makeTask("obfuscateJar", ObfuscateTask.class);
        obfTask.setSrg(delayedFile(PACKAGED_SRG));
        obfTask.setReverse(true);
        obfTask.setOutJar(delayedFile("{BUILD_DIR}/recompObfed.jar"));
        obfTask.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
        obfTask.dependsOn("generateProjects", "extractFmlSources", "fixMappings");

        GenBinaryPatches patchTask = makeTask("genBinPatches", GenBinaryPatches.class);
        patchTask.setCleanClient(delayedFile(JAR_CLIENT_FRESH));
        patchTask.setCleanServer(delayedFile(JAR_SERVER_FRESH));
        patchTask.setCleanMerged(delayedFile(JAR_MERGED));
        patchTask.setDirtyJar(delayedFile("{BUILD_DIR}/recompObfed.jar"));
        patchTask.setDeobfDataLzma(delayedFile(DEOBF_DATA));
        patchTask.setOutJar(delayedFile("{BUILD_DIR}/binPatches.jar"));
        patchTask.setSrg(delayedFile(PACKAGED_SRG));
        patchTask.setPatchList(delayedFileTree(FML_PATCH_DIR));
        patchTask.dependsOn("obfuscateJar", "compressDeobfData", "fixMappings");
    }

    @SuppressWarnings("serial")
    private void packageTasks()
    {
        ChangelogTask changelog = makeTask("createChangelog", ChangelogTask.class);
        changelog.getOutputs().upToDateWhen(CALL_FALSE);
        changelog.setServerRoot("http://ci.jenkins.minecraftforge.net/");
        changelog.setJobName("fml");
        changelog.setAuthName("console_script");
        changelog.setAuthPassword("dc6d48ca20a474beeac280a9a16a926e");
        changelog.setTargetBuild(delayedString("{BUILD_NUM}"));
        changelog.setOutput(delayedFile(CHANGELOG));

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        uni.setAppendix("universal");
        uni.getInputs().file(delayedFile(JSON_REL));
        uni.getOutputs().upToDateWhen(CALL_FALSE);
        uni.from(delayedZipTree("{BUILD_DIR}/binPatches.jar"));
        uni.from(delayedFileTree(FML_CLIENT));
        uni.from(delayedFileTree(FML_COMMON));
        uni.exclude("*.java", "**.java", "**/*.java"); // ignore java files.
        uni.exclude("devbinpatches.pack.lzma");
        uni.setIncludeEmptyDirs(false); // no empty folders
        uni.setManifest(new Closure<Object>(project)
        {
            public Object call()
            {
                Manifest mani = (Manifest)getDelegate();
                mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
                mani.getAttributes().put("Class-Path", FmlPlugin.this.getServerClassPath(FmlPlugin.this.delayedFile(JSON_REL).call()));
                return null;
            }
        });
        uni.dependsOn("genBinPatches", "createChangelog");
        project.getArtifacts().add("archives", uni);
        
        Zip installer = makeTask("packageInstaller", Zip.class);
        installer.from(new Closure<File>(project) {
            public File call()
            {
                return uni.getArchivePath();
            }
        });
        installer.from(delayedFile(FmlConstants.JSON_REL));
        installer.from(delayedFile("{FML_DIR}/jsons/big_logo.png.png"));
        installer.from(delayedFile(FmlConstants.JSON_REL));
        installer.rename("\\w+\\.json", "install_profile.json");
        installer.from(delayedZipTree(Constants.INSTALLER),  new Closure<Object>(project) {
            @Override
            public Object call()
            {
                CopySpec spec = (CopySpec) getDelegate();
                spec.exclude("*.json", "*.png");
                return null;
            }
            
            @Override
            public Object call(Object obj)
            {
                return call();
            }
        });
        installer.dependsOn("packageUniversal", "downloadBaseInstaller");
        installer.setExtension("zip");
    }
    
    private String getServerClassPath(File json)
    {
        try
        {
            JsonNode node = Constants.PARSER.parse(Files.newReader(json, Charset.defaultCharset()));
    
            StringBuilder buf = new StringBuilder();
    
            for (JsonNode lib : node.getArrayNode("versionInfo", "libraries"))
            {
                if (lib.isNode("serverreq") && lib.getBooleanValue("serverreq"))
                {
                    String[] pts = lib.getStringValue("name").split(":");
                    buf.append(String.format("libraries/%s/%s/%s/%s-%s.jar ", pts[0], pts[1], pts[2], pts[1], pts[2]));
                }
            }
            buf.append(delayedString("minecraft_server.{MC_VERSION}").call());
            return buf.toString();
        }
        catch(Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private <T extends Task> T makeTask(String name, Class<T> type)
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

    public static String getVersionFromGit(Project project)
    {
        String fullVersion = runGit(project, "describe", "--long");
        fullVersion = fullVersion.replace('-', '.').replaceAll("[^0-9.]", ""); //Normalize splitter, and remove non-numbers
        String[] pts = fullVersion.split("\\.");

        String major = pts[0];
        String minor = pts[1];
        String revision = pts[2];
        String build = "0";

        if (System.getenv().containsKey("BUILD_NUMBER"))
        {
            build = System.getenv("BUILD_NUMBER");
        }

        String branch = null;
        if (!System.getenv().containsKey("GIT_BRANCH"))
        {
            branch = runGit(project, "rev-parse", "--abbrev-ref", "head");
        }
        else
        {
            branch = System.getenv("GIT_BRANCH");
            branch = branch.substring(branch.lastIndexOf('/') + 1);
        }

        if (branch != null && branch.equals("master"))
        {
            branch = null;
        }

        StringBuilder out = new StringBuilder();
        out.append(DelayedString.resolve(Constants.MC_VERSION, project, new IDelayedResolver[]{new FmlPlugin()})).append('-'); // Somehow configure this?
        out.append(major).append('.').append(minor).append('.').append(revision).append('.').append(build);
        if (branch != null)
        {
            out.append('-').append(branch);
        }

        return out.toString();
    }

    private static String runGit(final Project project, final String... args)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        project.exec(new Closure<ExecSpec>(project, project)
        {
            private static final long serialVersionUID = -8561907087962634978L;

            @Override
            public ExecSpec call()
            {
                ExecSpec exec = (ExecSpec) getDelegate();
                exec.setExecutable("git");
                exec.args((Object[]) args);
                exec.setStandardOutput(out);
                exec.setWorkingDir(project.getProjectDir());
                return exec;
            }
        });

        return out.toString().trim();
    }

    private DelayedString delayedString(String path)
    {
        return new DelayedString(project, path, this);
    }

    private DelayedFile delayedFile(String path)
    {
        return new DelayedFile(project, path, this);
    }

    private DelayedFileTree delayedFileTree(String path)
    {
        return new DelayedFileTree(project, path, this);
    }

    private DelayedFileTree delayedZipTree(String path)
    {
        return new DelayedFileTree(project, path, true, this);
    }
    
    private void applyPlugin(String plugin)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("plugin", plugin);

        project.apply(map);
    }

    @Override
    public String resolve(String patern, Project project, ExtensionObject extension)
    {
        patern = patern.replace("{MAPPINGS_DIR}", extension.getFmlDir() + "/conf");
        patern = patern.replace("{FML_DIR}", extension.getFmlDir());
        return patern;
    }
}

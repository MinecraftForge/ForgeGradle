package net.minecraftforge.gradle;

import static net.minecraftforge.gradle.Constants.*;
import static net.minecraftforge.gradle.FmlConstants.*;
//import edu.sc.seis.launch4j.Launch4jPluginExtension;
import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedString.IDelayedResolver;
import net.minecraftforge.gradle.tasks.ChangelogTask;
import net.minecraftforge.gradle.tasks.CompressLZMA;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.DelayedJar;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.ExtractTask;
import net.minecraftforge.gradle.tasks.FMLVersionPropTask;
import net.minecraftforge.gradle.tasks.FileFilterTask;
import net.minecraftforge.gradle.tasks.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.GeneratePatches;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.MergeMappingsTask;
import net.minecraftforge.gradle.tasks.ObfuscateTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.ProjectTask;
import net.minecraftforge.gradle.tasks.SubprojectTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.CopySpec;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.process.ExecSpec;

import argo.jdom.JsonNode;

import com.google.common.io.Files;

public class FmlPlugin extends BasePlugin
{

    private static final String[] JAVA_FILES = new String[]{"**.java", "*.java", "**/*.java"};

    @Override
    public void apply(Project project)
    {
        super.apply(project);
        
        //configureLaunch4J();
        mappingFixTask();
        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        sourceCopyTasks();
        packageTasks();

        // the master setup task.
        Task task = makeTask("setupFML", DefaultTask.class);
        task.dependsOn("extractFmlSources", "generateProjects", "eclipse");
        task.setGroup("FML");
        
        // the master task.
        task = makeTask("buildPackages");
        task.dependsOn("launch4j", "packageUniversal", "createChangelog", "packageInstaller");
        task.setGroup("FML");
    }

    @Override protected String getDevJson(){ return JSON_DEV; }
    
    @Override
    protected void createObtainingTasks()
    {
        super.createObtainingTasks();
        DownloadTask task = makeTask("downloadBaseInstaller", DownloadTask.class);
        {
            task.setOutput(delayedFile(INSTALLER_BASE));
            task.setUrl(delayedString(INSTALLER_URL));
        }
    }
/*    
    private void configureLaunch4J()
    {
        
        Task task = project.getTasks().getByName("generateXmlConfig");
        task.dependsOn("packageInstaller");
        task.getInputs().file(delayedFile(Constants.INSTALLER));
        task.doFirst(new Closure(project, this) {
            @Override
            public Object call()
            {
                // get teh extension object
                Launch4jPluginExtension ext = (Launch4jPluginExtension) project.getExtensions().getByName("launch4j");
                //ext.setJar(((Zip)project.getTasks().getByName("packageInstaller")).getArchivePath().getAbsolutePath());
                //ext.setOutfile(((Zip)project.getTasks().getByName("packageInstaller")).getArchiveName().replace(".zip", ".exe"));
                
                try
                {
                    // set jar stuff
                    JarFile file = new JarFile(delayedFile(Constants.INSTALLER).call());
                    java.util.jar.Manifest man = file.getManifest();
                    ext.setMainClassName(man.getMainAttributes().getValue("Main-Class"));
                }
                catch (IOException e)
                {
                    Throwables.propagate(e); // -_-
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
*/
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

    protected void createJarProcessTasks()
    {
        MergeJarsTask task = makeTask("mergeJars", MergeJarsTask.class);
        {
            task.setClient(delayedFile(JAR_CLIENT_FRESH));
            task.setServer(delayedFile(JAR_SERVER_FRESH));
            task.setOutJar(delayedFile(JAR_MERGED));
            task.setMergeCfg(delayedFile(MERGE_CFG));
            task.dependsOn("downloadClient", "downloadServer");
        }

        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(JAR_MERGED));
            task2.setExceptorJar(delayedFile(EXCEPTOR));
            task2.setOutJar(delayedFile(JAR_SRG));
            task2.setSrg(delayedFile(PACKAGED_SRG));
            task2.setExceptorCfg(delayedFile(PACKAGED_EXC));
            task2.addTransformer(delayedFile(FML_COMMON + "/fml_at.cfg"));
            task2.dependsOn("downloadMcpTools", "fixMappings", "mergeJars");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(JAR_SRG));
            task3.setOutJar(delayedFile(ZIP_DECOMP));
            task3.setFernFlower(delayedFile(FERNFLOWER));
            task3.setPatch(delayedFile(PACKAGED_PATCH));
            task3.setAstyleConfig(delayedFile(ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar", "fixMappings");
        }
        
        PatchJarTask task4 = makeTask("fmlPatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_DECOMP));
            task4.setOutJar(delayedFile(ZIP_FML));
            task4.setInPatches(delayedFile(FML_PATCH_DIR));
            task4.dependsOn("decompile");
        }
    }

    private void sourceCopyTasks()
    {   
        ExtractTask task = makeTask("extractWorkspace", ExtractTask.class);
        {
            task.from(delayedFile(FML_ECLIPSE_WS));
            task.into(delayedFile(WORKSPACE));
        }

        task = makeTask("extractMcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_DECOMP));
            task.into(delayedFile(ECLIPSE_CLEAN + "/src/main/resources"));
            task.dependsOn("extractWorkspace", "decompile");
        }

        Copy copy = makeTask("copyStart", Copy.class);
        {
            copy.from(delayedFile("{MAPPINGS_DIR}/patches"));
            copy.include("Start.java");
            copy.into(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
            copy.dependsOn("extractMcResources");
        }

        task = makeTask("extractMcSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_DECOMP));
            task.into(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
            task.dependsOn("copyStart");
        }

        task = makeTask("extractFmlResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(ZIP_FML));
            task.into(delayedFile(ECLIPSE_FML + "/src/resources"));
            task.dependsOn("fmlPatchJar", "extractWorkspace");
        }

        copy = makeTask("copyDeobfData", Copy.class);
        {
            copy.from(delayedFile(DEOBF_DATA));
            copy.into(delayedFile(ECLIPSE_FML + "/src/resources"));
            copy.dependsOn("extractFmlResources", "compressDeobfData");
        }

        task = makeTask("extractFmlSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.exclude("cpw/**");
            task.from(delayedFile(ZIP_FML));
            task.into(delayedFile(ECLIPSE_FML + "/src/minecraft"));
            task.dependsOn("copyDeobfData");
        }

    }

    private void createProjectTasks()
    {
        ProjectTask task = makeTask("generateProjectClean", ProjectTask.class);
        {
            task.setTargetDir(delayedFile(ECLIPSE_CLEAN));
            task.setJson(delayedFile(JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectFML", ProjectTask.class);
        {
            task.setJson(delayedFile(JSON_DEV));
            task.setTargetDir(delayedFile(ECLIPSE_FML));
            
            task.addSource(delayedFile(ECLIPSE_FML + "/src/minecraft")); // Minecraft's base files
            task.addSource(delayedFile(FML_CLIENT)); // Eventually merge this into a single 'fml_source' in the repository
            task.addSource(delayedFile(FML_COMMON));

            task.addResource(delayedFile(ECLIPSE_FML + "/src/resources"));
            task.addResource(delayedFile(FML_CLIENT)); // Eventually change to 'fml_resources' in the repo
            task.addResource(delayedFile(FML_COMMON));
            
            task.dependsOn("extractNatives");
        }

        makeTask("generateProjects").dependsOn("generateProjectClean", "generateProjectFML");
    }

    private void createEclipseTasks()
    {
        SubprojectTask task = makeTask("eclipseClean", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_CLEAN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractMcSource", "generateProjects");
        }

        task = makeTask("eclipseFML", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractFmlSources", "generateProjects");
        }

        makeTask("eclipse").dependsOn("eclipseClean", "eclipseFML");
    }

    private void createMiscTasks()
    {
        CompressLZMA task = makeTask("compressDeobfData", CompressLZMA.class);
        {
            task.setInputFile(delayedFile(PACKAGED_SRG));
            task.setOutputFile(delayedFile(DEOBF_DATA));
            task.dependsOn("fixMappings");
        }

        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(PATCH_DIR));
            task2.setOriginalDir(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
            task2.setChangedDir(delayedFile(ECLIPSE_FML + "/src/minecraft"));
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.setGroup("FML");
        }

        Delete clean = makeTask("cleanFml", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(PACKAGED_SRG));
            obf.setReverse(true);
            obf.setOutJar(delayedFile("{BUILD_DIR}/recompObfed.jar"));
            obf.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
            obf.dependsOn("generateProjects", "extractFmlSources", "fixMappings");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(JAR_MERGED));
            task3.setDirtyJar(delayedFile("{BUILD_DIR}/recompObfed.jar"));
            task3.setDeobfDataLzma(delayedFile(DEOBF_DATA));
            task3.setOutJar(delayedFile("{BUILD_DIR}/binPatches.jar"));
            task3.setSrg(delayedFile(PACKAGED_SRG));
            task3.setPatchList(delayedFileTree(FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData", "fixMappings");
        }
    }

    @SuppressWarnings("serial")
    private void packageTasks()
    {
        ChangelogTask log = makeTask("createChangelog", ChangelogTask.class);
        {
            log.getOutputs().upToDateWhen(CALL_FALSE);
            log.setServerRoot("http://ci.jenkins.minecraftforge.net/");
            log.setJobName("fml");
            log.setAuthName("console_script");
            log.setAuthPassword("dc6d48ca20a474beeac280a9a16a926e");
            log.setTargetBuild(delayedString("{BUILD_NUM}"));
            log.setOutput(delayedFile(CHANGELOG));
        }

        FMLVersionPropTask prop = makeTask("createVersionProperties", FMLVersionPropTask.class);
        {
            prop.getOutputs().upToDateWhen(CALL_FALSE);
            prop.setOutputFile(delayedFile(FML_VERSIONF));
        }

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        {
            uni.setAppendix("universal");
            uni.getInputs().file(delayedFile(JSON_REL));
            uni.getOutputs().upToDateWhen(CALL_FALSE);
            uni.from(delayedZipTree("{BUILD_DIR}/binPatches.jar"));
            uni.from(delayedFileTree(FML_CLIENT));
            uni.from(delayedFileTree(FML_COMMON));
            uni.from(delayedFile(FML_VERSIONF));
            uni.from(delayedFile(DEOBF_DATA));
            uni.exclude(JAVA_FILES);
            uni.exclude("devbinpatches.pack.lzma");
            uni.setIncludeEmptyDirs(false);
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
            uni.dependsOn("genBinPatches", "createChangelog", "createVersionProperties");
        }
        project.getArtifacts().add("archives", uni);
        
        FileFilterTask task = makeTask("generateInstallJson", FileFilterTask.class);
        {
            task.setInputFile(delayedFile(JSON_REL));
            task.setOutputFile(delayedFile(INSTALL_PROFILE));
            task.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            task.addReplacement("@version@",           delayedString("{VERSION}"));
            task.addReplacement("@universal_jar@", new Closure<String>(project)
            {
                public String call()
                {
                    return uni.getArchiveName();
                }
            });
            task.addReplacement("@timestamp@", new Closure<String>(project)
            {
                public String call()
                {
                    return (new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")).format(new Date());
                }
            });
        }
        
        Zip inst = makeTask("packageInstaller", Zip.class);
        {
            inst.setAppendix("installer");
            inst.from(new Closure<File>(project) {
                public File call()
                {
                    return uni.getArchivePath();
                }
            });
            inst.from(delayedFile(INSTALL_PROFILE));
            inst.from(delayedFile("{FML_DIR}/jsons/big_logo.png"));
            inst.from(delayedZipTree(INSTALLER_BASE),  new Closure<Object>(project) {
                @Override
                public Object call()
                {
                    ((CopySpec)getDelegate()).exclude("*.json", "*.png");
                    return null;
                }
                
                @Override public Object call(Object obj){ return call(); }
            });
            inst.dependsOn("packageUniversal", "downloadBaseInstaller", "generateInstallJson");
            inst.setExtension("jar");
        }
        project.getArtifacts().add("archives", inst);
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

    @Override
    public String resolve(String patern, Project project, ExtensionObject extension)
    {
        patern = patern.replace("{MAPPINGS_DIR}", extension.getFmlDir() + "/conf");
        patern = patern.replace("{FML_DIR}", extension.getFmlDir());
        return super.resolve(patern, project, extension);
    }
}

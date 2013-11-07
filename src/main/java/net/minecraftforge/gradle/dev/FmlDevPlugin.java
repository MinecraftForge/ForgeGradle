package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.common.Constants.BINPATCH_TMP;
import static net.minecraftforge.gradle.common.Constants.CALL_FALSE;
import static net.minecraftforge.gradle.common.Constants.DEOBF_DATA;
import static net.minecraftforge.gradle.common.Constants.ECLIPSE_CLEAN;
import static net.minecraftforge.gradle.common.Constants.ECLIPSE_FML;
import static net.minecraftforge.gradle.common.Constants.EXCEPTOR;
import static net.minecraftforge.gradle.common.Constants.FERNFLOWER;
import static net.minecraftforge.gradle.common.Constants.INSTALLER_BASE;
import static net.minecraftforge.gradle.common.Constants.INSTALLER_URL;
import static net.minecraftforge.gradle.common.Constants.INSTALL_PROFILE;
import static net.minecraftforge.gradle.common.Constants.JAR_CLIENT_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.JAR_SERVER_FRESH;
import static net.minecraftforge.gradle.common.Constants.JAR_SRG;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_EXC;
import static net.minecraftforge.gradle.common.Constants.PACKAGED_SRG;
import static net.minecraftforge.gradle.common.Constants.PATCH_DIR;
import static net.minecraftforge.gradle.common.Constants.REOBF_TMP;
import static net.minecraftforge.gradle.common.Constants.WORKSPACE;
import static net.minecraftforge.gradle.common.Constants.ZIP_DECOMP;
import static net.minecraftforge.gradle.common.Constants.ZIP_FML;
import static net.minecraftforge.gradle.dev.FmlConstants.ASTYLE_CFG;
import static net.minecraftforge.gradle.dev.FmlConstants.CHANGELOG;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_CLIENT;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_COMMON;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_ECLIPSE_WS;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_LICENSE;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_LOGO;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_PATCH_DIR;
import static net.minecraftforge.gradle.dev.FmlConstants.FML_VERSIONF;
import static net.minecraftforge.gradle.dev.FmlConstants.JOINED_EXC;
import static net.minecraftforge.gradle.dev.FmlConstants.JOINED_SRG;
import static net.minecraftforge.gradle.dev.FmlConstants.JSON_DEV;
import static net.minecraftforge.gradle.dev.FmlConstants.JSON_REL;
import static net.minecraftforge.gradle.dev.FmlConstants.MCP_PATCH;
import static net.minecraftforge.gradle.dev.FmlConstants.MERGE_CFG;
import static net.minecraftforge.gradle.dev.FmlConstants.PACKAGED_PATCH;
import static net.minecraftforge.gradle.dev.FmlConstants.PACK_CSV;
//import edu.sc.seis.launch4j.Launch4jPluginExtension;
import groovy.lang.Closure;

import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraftforge.gradle.CopyInto;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.abstractutil.DelayedJar;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.abstractutil.FileFilterTask;
import net.minecraftforge.gradle.tasks.dev.ChangelogTask;
import net.minecraftforge.gradle.tasks.dev.CompressLZMA;
import net.minecraftforge.gradle.tasks.dev.FMLVersionPropTask;
import net.minecraftforge.gradle.tasks.dev.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.dev.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.dev.GeneratePatches;
import net.minecraftforge.gradle.tasks.dev.MergeMappingsTask;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;
import net.minecraftforge.gradle.tasks.dev.SubprojectTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

import argo.jdom.JsonNode;

import com.google.common.io.Files;

public class FmlDevPlugin extends DevBasePlugin
{
    private static final String[] JAVA_FILES = new String[] { "**.java", "*.java", "**/*.java" };

    @Override
    public void applyPlugin()
    {
        // set fmlDir
        getExtension().setFmlDir(".");
        
        createDownloadTasks();
        //configureLaunch4J();
        creatMappingFixTask();
        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        createSourceCopyTasks();
        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupFML", DefaultTask.class);
        task.dependsOn("extractFmlSources", "generateProjects", "eclipse");
        task.setGroup("FML");

        // the master task.
        task = makeTask("buildPackages");
        //task.dependsOn("launch4j", "packageUniversal", "createChangelog", "packageInstaller");
        task.dependsOn("createChangelog", "packageUniversal", "packageInstaller", "packageInstaller");
        task.setGroup("FML");
    }

    @Override
    protected String getDevJson()
    {
        return DelayedBase.resolve(FmlConstants.JSON_DEV, project, this);
    }

    private void createDownloadTasks()
    {
        DownloadTask task = makeTask("downloadBaseInstaller", DownloadTask.class);
        {
            task.setOutput(delayedFile(INSTALLER_BASE));
            task.setUrl(delayedString(INSTALLER_URL));
        }
    }

    /*
     * private void configureLaunch4J()
     * {
     * Task task = project.getTasks().getByName("generateXmlConfig");
     * task.dependsOn("packageInstaller");
     * task.getInputs().file(delayedFile(Constants.INSTALLER));
     * task.doFirst(new Closure(project, this) {
     * @Override
     * public Object call()
     * {
     * // get teh extension object
     * Launch4jPluginExtension ext = (Launch4jPluginExtension) project.getExtensions().getByName("launch4j");
     * //ext.setJar(((Zip)project.getTasks().getByName("packageInstaller")).getArchivePath().getAbsolutePath());
     * //ext.setOutfile(((Zip)project.getTasks().getByName("packageInstaller")).getArchiveName().replace(".zip", ".exe"));
     * try
     * {
     * // set jar stuff
     * JarFile file = new JarFile(delayedFile(Constants.INSTALLER).call());
     * java.util.jar.Manifest man = file.getManifest();
     * ext.setMainClassName(man.getMainAttributes().getValue("Main-Class"));
     * }
     * catch (IOException e)
     * {
     * Throwables.propagate(e); // -_-
     * }
     * return null;
     * }
     * @Override
     * public Object call(Object obj)
     * {
     * return call();
     * }
     * @Override
     * public Object call(Object... obj)
     * {
     * return call();
     * }
     * });
     * }
     */
    /**
     * Fixes the SRG, EXC and MCP patch files to use the package refractor.
     */
    private void creatMappingFixTask()
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
            task2.setOutCleanJar(delayedFile(JAR_SRG));
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

    private void createSourceCopyTasks()
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
        GenDevProjectsTask task = makeTask("generateProjectClean", GenDevProjectsTask.class);
        {
            task.setTargetDir(delayedFile(ECLIPSE_CLEAN));
            task.setJson(delayedFile(JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectFML", GenDevProjectsTask.class);
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
            obf.setOutJar(delayedFile(REOBF_TMP));
            obf.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
            obf.dependsOn("generateProjects", "extractFmlSources", "fixMappings");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(JAR_MERGED));
            task3.setDirtyJar(delayedFile(REOBF_TMP));
            task3.setDeobfDataLzma(delayedFile(DEOBF_DATA));
            task3.setOutJar(delayedFile(BINPATCH_TMP));
            task3.setSrg(delayedFile(PACKAGED_SRG));
            task3.setPatchList(delayedFileTree(FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData", "fixMappings");
        }
    }

    @SuppressWarnings("serial")
    private void createPackageTasks()
    {
        ChangelogTask log = makeTask("createChangelog", ChangelogTask.class);
        {
            log.getOutputs().upToDateWhen(CALL_FALSE);
            log.setServerRoot(delayedString("{JENKINS_SERVER}"));
            log.setJobName(delayedString("{JENKINS_JOB}"));
            log.setAuthName(delayedString("{JENKINS_AUTH_NAME}"));
            log.setAuthPassword(delayedString("{JENKINS_AUTH_PASSWORD}"));
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
            uni.setClassifier("universal");
            uni.getInputs().file(delayedFile(JSON_REL));
            uni.getOutputs().upToDateWhen(CALL_FALSE);
            uni.from(delayedZipTree(BINPATCH_TMP));
            uni.from(delayedFileTree(FML_CLIENT));
            uni.from(delayedFileTree(FML_COMMON));
            uni.from(delayedFile(FML_VERSIONF));
            uni.from(delayedFile(FML_LICENSE));
            uni.from(delayedFile(DEOBF_DATA));
            uni.from(delayedFile(CHANGELOG));
            uni.exclude(JAVA_FILES);
            uni.exclude("devbinpatches.pack.lzma");
            uni.setIncludeEmptyDirs(false);
            uni.setManifest(new Closure<Object>(project)
            {
                public Object call()
                {
                    Manifest mani = (Manifest) getDelegate();
                    mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
                    mani.getAttributes().put("Class-Path", FmlDevPlugin.this.getServerClassPath(FmlDevPlugin.this.delayedFile(JSON_REL).call()));
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
            task.addReplacement("@version@", delayedString("{VERSION}"));
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
            inst.setClassifier("installer");
            inst.from(new Closure<File>(project) {
                public File call()
                {
                    return uni.getArchivePath();
                }
            });
            inst.from(delayedFile(INSTALL_PROFILE));
            inst.from(delayedFile(CHANGELOG));
            inst.from(delayedFile(FML_LICENSE));
            inst.from(delayedFile(FML_LOGO));
            inst.from(delayedZipTree(INSTALLER_BASE), new CopyInto(".", "!*.json", "!.png"));
            inst.dependsOn("packageUniversal", "downloadBaseInstaller", "generateInstallJson");
            inst.setExtension("jar");
        }
        project.getArtifacts().add("archives", inst);
        
        final Zip patchZip = makeTask("zipPatches", Zip.class);
        {
            patchZip.from(delayedFile(FmlConstants.FML_PATCH_DIR));
            patchZip.setArchiveName("fmlpatches.zip");
        }
        
        final SubprojectTask javadocJar = makeTask("genJavadocs", SubprojectTask.class);
        {
            javadocJar.setBuildFile(delayedFile(ECLIPSE_FML + "/build.gradle"));
            javadocJar.setTasks("jar");
            javadocJar.setConfigureTask(new Closure<Object>(this, null) {
                public Object call(Object obj)
                {
                    Jar task = (Jar) obj;
                    File file = delayedFile(Constants.JAVADOC_TMP).call();
                    task.setDestinationDir(file.getParentFile());
                    task.setArchiveName(file.getName());
                    
                    return null;
                }
            });
        }
        
        Zip userDev = makeTask("packageUserDev", Zip.class);
        {
            userDev.setClassifier("userdev");
            userDev.from(delayedFile(JSON_DEV));
            userDev.from(delayedFile(Constants.JAVADOC_TMP));
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return patchZip.getArchivePath();
                }
            });
            userDev.from(delayedZipTree(BINPATCH_TMP), new CopyInto("", "devbinpatches.pack.lzma"));
            userDev.from(delayedZipTree(BINPATCH_TMP), new CopyInto("bin", "**/*.class"));
            userDev.from(delayedFileTree("{FML_DIR}/common"), new CopyInto("src"));
            userDev.from(delayedFileTree("{FML_DIR}/client"), new CopyInto("src"));
            userDev.from(delayedFileTree(MERGE_CFG), new CopyInto("conf"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}"), new CopyInto("conf", "astyle.cfg"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}"), new CopyInto("mappings", "*.csv", "!packages.csv"));
            userDev.from(delayedFile(Constants.PACKAGED_SRG), new CopyInto("conf"));
            userDev.from(delayedFile(Constants.PACKAGED_EXC), new CopyInto("conf"));
            userDev.from(delayedFile(FmlConstants.PACKAGED_PATCH), new CopyInto("conf"));
            userDev.rename(".+?\\.json", "dev.json");
            userDev.rename(".+?\\.srg", "packaged.srg");
            userDev.rename(".+?\\.exc", "packaged.exc");
            userDev.rename(".+?\\.patch", "packaged.patch");
            userDev.setIncludeEmptyDirs(false);
            userDev.dependsOn("packageUniversal", "zipPatches");
            userDev.setExtension("jar");
        }
        project.getArtifacts().add("archives", userDev);
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
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("rawtypes")
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
        out.append(DelayedBase.resolve(Constants.MC_VERSION, project, (IDelayedResolver) project.getPlugins().findPlugin("fmldev"))).append('-'); // Somehow configure this?
        out.append(major).append('.').append(minor).append('.').append(revision).append('.').append(build);
        if (branch != null)
        {
            out.append('-').append(branch);
        }

        return out.toString();
    }
    
    protected DelayedString   delayedString  (String path){ return new DelayedString  (project, path, this); }
    protected DelayedFile     delayedFile    (String path){ return new DelayedFile    (project, path, this); }
    protected DelayedFileTree delayedFileTree(String path){ return new DelayedFileTree(project, path, this); }
    protected DelayedFileTree delayedZipTree (String path){ return new DelayedFileTree(project, path, true, this); }
}

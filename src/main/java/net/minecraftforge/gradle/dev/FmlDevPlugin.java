package net.minecraftforge.gradle.dev;

//import edu.sc.seis.launch4j.Launch4jPluginExtension;
import static net.minecraftforge.gradle.dev.DevConstants.CROWDIN_FORGEID;
import static net.minecraftforge.gradle.dev.DevConstants.CROWDIN_ZIP;
import static net.minecraftforge.gradle.dev.DevConstants.EXC_MODIFIERS_DIRTY;
import groovy.lang.Closure;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraftforge.gradle.CopyInto;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ApplyS2STask;
import net.minecraftforge.gradle.tasks.CrowdinDownloadTask;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.ExtractS2SRangeTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.DelayedJar;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.abstractutil.FileFilterTask;
import net.minecraftforge.gradle.tasks.dev.ChangelogTask;
import net.minecraftforge.gradle.tasks.dev.FMLVersionPropTask;
import net.minecraftforge.gradle.tasks.dev.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.dev.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.dev.GeneratePatches;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;
import net.minecraftforge.gradle.tasks.dev.SubprojectTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;

public class FmlDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set fmlDir
        getExtension().setFmlDir(".");

        /* We dont need to add this to ALL this is only used for S2S
        // configure genSrg task.
        GenSrgTask genSrgTask = (GenSrgTask) project.getTasks().getByName("genSrgs");
        {
            // find all the exc & srg files in the resources.
            for (File f : project.fileTree(delayedFile(DevConstants.FML_RESOURCES).call()).getFiles())
            {
                if(f.getPath().endsWith(".exc"))
                    genSrgTask.addExtraExc(f);
                else if(f.getPath().endsWith(".srg"))
                    genSrgTask.addExtraSrg(f);
            }
        }
        */

        //configureLaunch4J();
        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        createSourceCopyTasks();
        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupFML", DefaultTask.class);
        task.dependsOn("extractFmlSources", "generateProjects", "eclipse", "copyAssets");
        task.setGroup("FML");

        // the master task.
        task = makeTask("buildPackages");
        task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "packageUserDev", "packageSrc");
        task.setGroup("FML");

        // clean decompile task
        Delete delTask = makeTask("cleanDecompile", Delete.class);
        delTask.delete(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
        delTask.delete(delayedFile(DevConstants.ECLIPSE_FML_SRC));
        delTask.delete(delayedFile(DevConstants.ZIP_DECOMP_FML));
        delTask.delete(delayedFile(DevConstants.ZIP_PATCHED_FML));
        delTask.setGroup("Clean");
    }

    protected void createJarProcessTasks()
    {

        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setOutCleanJar(delayedFile(DevConstants.JAR_SRG_FML));
            task2.setSrg(delayedFile(DevConstants.NOTCH_2_SRG_SRG));
            task2.setExceptorCfg(delayedFile(DevConstants.JOINED_EXC));
            task2.setExceptorJson(delayedFile(DevConstants.EXC_JSON));
            task2.addTransformerClean(delayedFile(DevConstants.FML_RESOURCES + "/fml_at.cfg"));
            task2.setApplyMarkers(true);
            task2.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(DevConstants.JAR_SRG_FML));
            task3.setOutJar(delayedFile(DevConstants.ZIP_DECOMP_FML));
            task3.setFernFlower(delayedFile(Constants.FERNFLOWER));
            task3.setPatch(delayedFile(DevConstants.MCP_PATCH_DIR));
            task3.setAstyleConfig(delayedFile(DevConstants.ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar");
        }

        RemapSourcesTask remapTask = makeTask("remapCleanJar", RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(DevConstants.ZIP_DECOMP_FML));
            remapTask.setOutJar(delayedFile(DevConstants.REMAPPED_CLEAN));
            remapTask.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            remapTask.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            remapTask.setParamsCsv(delayedFile(DevConstants.PARAMS_CSV));
            remapTask.setDoesCache(false);
            remapTask.setNoJavadocs();
            remapTask.dependsOn("decompile");
        }

        ProcessSrcJarTask task5 = makeTask("fmlPatchJar", ProcessSrcJarTask.class);
        {
            task5.setInJar(delayedFile(DevConstants.ZIP_DECOMP_FML));
            task5.setOutJar(delayedFile(DevConstants.ZIP_PATCHED_FML));
            task5.addStage("fml", delayedFile(DevConstants.FML_PATCH_DIR));
            task5.setDoesCache(false);
            task5.setMaxFuzz(2);
            task5.dependsOn("decompile");
        }

        remapTask = makeTask("remapDirtyJar", RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(DevConstants.ZIP_PATCHED_FML));
            remapTask.setOutJar(delayedFile(DevConstants.REMAPPED_DIRTY));
            remapTask.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            remapTask.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            remapTask.setParamsCsv(delayedFile(DevConstants.PARAMS_CSV));
            remapTask.setDoesCache(false);
            remapTask.setNoJavadocs();
            remapTask.dependsOn("fmlPatchJar");
        }
    }

    private void createSourceCopyTasks()
    {
        // COPY CLEAN STUFF
        ExtractTask task = makeTask("extractMcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(DevConstants.REMAPPED_CLEAN));
            task.into(delayedFile(DevConstants.ECLIPSE_CLEAN_RES));
            task.dependsOn("extractWorkspace", "remapCleanJar");
        }

        Copy copy = makeTask("copyStart", Copy.class);
        {
            copy.from(delayedFile("{FML_CONF_DIR}/patches"));
            copy.include("Start.java");
            copy.into(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
            copy.dependsOn("extractMcResources");
        }

        task = makeTask("extractMcSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(DevConstants.REMAPPED_CLEAN));
            task.into(delayedFile(DevConstants.ECLIPSE_CLEAN_SRC));
            task.dependsOn("copyStart");
        }

        // COPY FML STUFF
        task = makeTask("extractFmlResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(DevConstants.REMAPPED_DIRTY));
            task.into(delayedFile(DevConstants.ECLIPSE_FML_RES));
            task.dependsOn("remapDirtyJar", "extractWorkspace");
        }

        copy = makeTask("copyDeobfData", Copy.class);
        {
            copy.from(delayedFile(DevConstants.DEOBF_DATA));
            copy.from(delayedFile(DevConstants.FML_VERSIONF));
            copy.into(delayedFile(DevConstants.ECLIPSE_FML_RES));
            copy.dependsOn("extractFmlResources", "compressDeobfData");
        }

        task = makeTask("extractFmlSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.exclude("cpw/**");
            task.exclude("net/minecraftforge/fml/**");
            task.from(delayedFile(DevConstants.REMAPPED_DIRTY));
            task.into(delayedFile(DevConstants.ECLIPSE_FML_SRC));
            task.dependsOn("copyDeobfData");
        }
    }

    private void createProjectTasks()
    {
        GenDevProjectsTask task = makeTask("generateProjectClean", GenDevProjectsTask.class);
        {
            task.setTargetDir(delayedFile(DevConstants.ECLIPSE_CLEAN));
            task.setJson(delayedFile(DevConstants.JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json

            task.setMcVersion(delayedString("{MC_VERSION}"));
            task.setMappingChannel(delayedString("{MAPPING_CHANNEL}"));
            task.setMappingVersion(delayedString("{MAPPING_VERSION}"));

            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectFML", GenDevProjectsTask.class);
        {
            task.setJson(delayedFile(DevConstants.JSON_DEV));
            task.setTargetDir(delayedFile(DevConstants.ECLIPSE_FML));

            task.addSource(delayedFile(DevConstants.ECLIPSE_FML_SRC));
            task.addSource(delayedFile(DevConstants.FML_SOURCES));
            task.addTestSource(delayedFile(DevConstants.FML_TEST_SOURCES));

            task.addResource(delayedFile(DevConstants.ECLIPSE_FML_RES));
            task.addResource(delayedFile(DevConstants.FML_RESOURCES));
            task.addTestResource(delayedFile(DevConstants.FML_TEST_RES));

            task.setMcVersion(delayedString("{MC_VERSION}"));
            task.setMappingChannel(delayedString("{MAPPING_CHANNEL}"));
            task.setMappingVersion(delayedString("{MAPPING_VERSION}"));

            task.dependsOn("extractNatives","createVersionProperties");
        }

        makeTask("generateProjects").dependsOn("generateProjectClean", "generateProjectFML");
    }

    private void createEclipseTasks()
    {
        SubprojectTask task = makeTask("eclipseClean", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(DevConstants.ECLIPSE_CLEAN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractMcSource", "generateProjects");
        }

        task = makeTask("eclipseFML", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(DevConstants.ECLIPSE_FML + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractFmlSources", "generateProjects");
        }

        makeTask("eclipse").dependsOn("eclipseClean", "eclipseFML");
    }

    private void createMiscTasks()
    {
        DelayedFile rangeMap = delayedFile("{BUILD_DIR}/tmp/rangemap.txt");

        ExtractS2SRangeTask task = makeTask("extractRange", ExtractS2SRangeTask.class);
        {
            task.setLibsFromProject(delayedFile(DevConstants.ECLIPSE_FML + "/build.gradle"), "compile", true);
            task.addIn(delayedFile(DevConstants.ECLIPSE_FML_SRC));
            //task.addIn(delayedFile(DevConstants.FML_SOURCES));
            task.setExcOutput(delayedFile(DevConstants.EXC_MODIFIERS_DIRTY));
            task.setRangeMap(rangeMap);
        }

        ApplyS2STask task4 = makeTask("retroMapSources", ApplyS2STask.class);
        {
            task4.addIn(delayedFile(DevConstants.ECLIPSE_FML_SRC));
            task4.setOut(delayedFile(DevConstants.PATCH_DIRTY));
            task4.addSrg(delayedFile(DevConstants.MCP_2_SRG_SRG));
            task4.addExc(delayedFile(DevConstants.MCP_EXC));
            task4.addExc(delayedFile(DevConstants.SRG_EXC)); // both EXCs just in case.
            task4.setExcModifiers(delayedFile(EXC_MODIFIERS_DIRTY));
            task4.setRangeMap(rangeMap);
            task4.dependsOn("genSrgs", task);

            // find all the exc & srg files in the resources.
            for (File f : project.fileTree(delayedFile(DevConstants.FML_RESOURCES).call()).getFiles())
            {
                if(f.getPath().endsWith(".exc"))
                    task4.addExc(f);
                else if(f.getPath().endsWith(".srg"))
                    task4.addSrg(f);
            }
        }

        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(DevConstants.FML_PATCH_DIR));
            task2.setOriginal(delayedFile(DevConstants.ZIP_DECOMP_FML));
            task2.setChanged(delayedFile(DevConstants.PATCH_DIRTY));
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.setGroup("FML");
            task2.dependsOn("retroMapSources");
        }

        Delete clean = makeTask("cleanFml", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(DevConstants.MCP_2_NOTCH_SRG));
            obf.setExc(delayedFile(DevConstants.SRG_EXC));
            obf.setReverse(false);
            obf.setPreFFJar(delayedFile(DevConstants.JAR_SRG_FML));
            obf.setOutJar(delayedFile(DevConstants.REOBF_TMP));
            obf.setBuildFile(delayedFile(DevConstants.ECLIPSE_FML + "/build.gradle"));
            obf.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            obf.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            obf.dependsOn("generateProjects", "extractFmlSources", "genSrgs");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(Constants.JAR_MERGED));
            task3.setDirtyJar(delayedFile(DevConstants.REOBF_TMP));
            task3.setDeobfDataLzma(delayedFile(DevConstants.DEOBF_DATA));
            task3.setOutJar(delayedFile(DevConstants.BINPATCH_TMP));
            task3.setSrg(delayedFile(DevConstants.NOTCH_2_SRG_SRG));
            task3.addPatchList(delayedFileTree(DevConstants.FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData");
        }

        FMLVersionPropTask prop = makeTask("createVersionProperties", FMLVersionPropTask.class);
        {
            prop.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            prop.setOutputFile(delayedFile(DevConstants.FML_VERSIONF));
        }
    }

    @SuppressWarnings("serial")
    private void createPackageTasks()
    {
        CrowdinDownloadTask crowdin = makeTask("getLocalizations", CrowdinDownloadTask.class);
        {
            crowdin.setOutput(delayedFile(CROWDIN_ZIP));
            crowdin.setProjectId(CROWDIN_FORGEID);
            crowdin.setExtract(false);
        }

        ChangelogTask makeChangelog = makeTask("createChangelog", ChangelogTask.class);
        {
            makeChangelog.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            makeChangelog.setServerRoot(delayedString("{JENKINS_SERVER}"));
            makeChangelog.setJobName(delayedString("{JENKINS_JOB}"));
            makeChangelog.setAuthName(delayedString("{JENKINS_AUTH_NAME}"));
            makeChangelog.setAuthPassword(delayedString("{JENKINS_AUTH_PASSWORD}"));
            makeChangelog.setTargetBuild(delayedString("{BUILD_NUM}"));
            makeChangelog.setOutput(delayedFile(DevConstants.CHANGELOG));
        }

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        {
            uni.setClassifier("universal");
            uni.getInputs().file(delayedFile(DevConstants.JSON_REL));
            uni.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            uni.from(delayedZipTree(DevConstants.BINPATCH_TMP));
            uni.from(delayedFileTree(DevConstants.FML_RESOURCES));
            uni.from(delayedZipTree(DevConstants.CROWDIN_ZIP));
            uni.from(delayedFile(DevConstants.FML_VERSIONF));
            uni.from(delayedFile(DevConstants.FML_LICENSE));
            uni.from(delayedFile(DevConstants.FML_CREDITS));
            uni.from(delayedFile(DevConstants.DEOBF_DATA));
            uni.from(delayedFile(DevConstants.CHANGELOG));
            uni.exclude("devbinpatches.pack.lzma");
            uni.setIncludeEmptyDirs(false);
            uni.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            uni.setManifest(new Closure<Object>(project)
            {
                public Object call()
                {
                    Manifest mani = (Manifest) getDelegate();
                    mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
                    mani.getAttributes().put("TweakClass", delayedString("{FML_TWEAK_CLASS}").call());
                    mani.getAttributes().put("Class-Path", getServerClassPath(delayedFile(DevConstants.JSON_REL).call()));
                    return null;
                }
            });
            uni.dependsOn("genBinPatches", crowdin, makeChangelog, "createVersionProperties");
        }
        project.getArtifacts().add("archives", uni);

        FileFilterTask genInstallJson = makeTask("generateInstallJson", FileFilterTask.class);
        {
            genInstallJson.setInputFile(delayedFile(DevConstants.JSON_REL));
            genInstallJson.setOutputFile(delayedFile(DevConstants.INSTALL_PROFILE));
            genInstallJson.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            genInstallJson.addReplacement("@version@", delayedString("{VERSION}"));
            genInstallJson.addReplacement("@project@", delayedString("FML"));
            genInstallJson.addReplacement("@artifact@", delayedString("cpw.mods:fml:{MC_VERSION_SAFE}-{VERSION}"));
            genInstallJson.addReplacement("@universal_jar@", new Closure<String>(project)
            {
                public String call()
                {
                    return uni.getArchiveName();
                }
            });
            genInstallJson.addReplacement("@timestamp@", new Closure<String>(project)
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
            inst.from(delayedFile(DevConstants.INSTALL_PROFILE));
            inst.from(delayedFile(DevConstants.CHANGELOG));
            inst.from(delayedFile(DevConstants.FML_LICENSE));
            inst.from(delayedFile(DevConstants.FML_CREDITS));
            inst.from(delayedFile(DevConstants.FML_LOGO));
            inst.from(delayedZipTree(DevConstants.INSTALLER_BASE), new CopyInto("/", "!*.json", "!*.png"));
            inst.dependsOn(uni, "downloadBaseInstaller", genInstallJson);
            inst.setExtension("jar");
        }
        project.getArtifacts().add("archives", inst);

        final Zip patchZip = makeTask("zipPatches", Zip.class);
        {
            patchZip.from(delayedFile(DevConstants.FML_PATCH_DIR));
            patchZip.setArchiveName("fmlpatches.zip");
        }

        final Zip classZip = makeTask("jarClasses", Zip.class);
        {
            classZip.from(delayedZipTree(DevConstants.BINPATCH_TMP), new CopyInto("", "**/*.class"));
            classZip.setArchiveName("binaries.jar");
        }

        ExtractS2SRangeTask range = makeTask("userDevExtractRange", ExtractS2SRangeTask.class);
        {
            range.setLibsFromProject(delayedFile(DevConstants.ECLIPSE_FML + "/build.gradle"), "compile", true);
            range.addIn(delayedFile(DevConstants.FML_SOURCES));
            range.setRangeMap(delayedFile(DevConstants.USERDEV_RANGEMAP));
            range.dependsOn("generateProjects", "extractFmlSources");
        }

        ApplyS2STask s2s = makeTask("userDevSrgSrc", ApplyS2STask.class);
        {
            s2s.addIn(delayedFile(DevConstants.FML_SOURCES));
            s2s.setOut(delayedFile(DevConstants.USERDEV_SRG_SRC));
            s2s.addSrg(delayedFile(DevConstants.MCP_2_SRG_SRG));
            s2s.addExc(delayedFile(DevConstants.JOINED_EXC));
            s2s.setRangeMap(delayedFile(DevConstants.USERDEV_RANGEMAP));
            s2s.dependsOn("genSrgs", range);
            s2s.getOutputs().upToDateWhen(Constants.CALL_FALSE); //Fucking caching.

            // find all the exc & srg files in the resources.
            for (File f : project.fileTree(delayedFile(DevConstants.FML_RESOURCES).call()).getFiles())
            {
                if(f.getPath().endsWith(".exc"))
                    s2s.addExc(f);
                else if(f.getPath().endsWith(".srg"))
                    s2s.addSrg(f);
            }
        }

        Zip userDev = makeTask("packageUserDev", Zip.class);
        {
            userDev.setClassifier("userdev");
            userDev.from(delayedFile(DevConstants.JSON_DEV));
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return patchZip.getArchivePath();
                }
            });
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return classZip.getArchivePath();
                }
            });
            userDev.from(delayedFile(DevConstants.CHANGELOG));
            userDev.from(delayedZipTree(DevConstants.BINPATCH_TMP), new CopyInto("", "devbinpatches.pack.lzma"));
            userDev.from(delayedFileTree("{FML_DIR}/src/main/resources"), new CopyInto("src/main/resources"));
            userDev.from(delayedZipTree(DevConstants.CROWDIN_ZIP), new CopyInto("src/main/resources"));
            userDev.from(delayedFile(DevConstants.FML_VERSIONF), new CopyInto("src/main/resources"));
            userDev.from(delayedZipTree(DevConstants.USERDEV_SRG_SRC), new CopyInto("src/main/java"));
            userDev.from(delayedFile(DevConstants.DEOBF_DATA), new CopyInto("src/main/resources/"));
            userDev.from(delayedFile(DevConstants.MERGE_CFG), new CopyInto("conf"));
            userDev.from(delayedFileTree("{FML_CONF_DIR}"), new CopyInto("conf", "astyle.cfg", "exceptor.json", "*.csv", "!packages.csv"));
            userDev.from(delayedFile(DevConstants.NOTCH_2_SRG_SRG), new CopyInto("conf"));
            userDev.from(delayedFile(DevConstants.SRG_EXC), new CopyInto("conf"));
            userDev.from(delayedFileTree("{FML_CONF_DIR}/patches"), new CopyInto("conf"));
            userDev.rename(".+-dev\\.json", "dev.json");
            userDev.rename(".+?\\.srg", "packaged.srg");
            userDev.rename(".+?\\.exc", "packaged.exc");
            userDev.setIncludeEmptyDirs(false);
            uni.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
            userDev.dependsOn("packageUniversal", crowdin, patchZip, classZip, "createVersionProperties", s2s);
            userDev.setExtension("jar");
        }
        project.getArtifacts().add("archives", userDev);

        Zip src = makeTask("packageSrc", Zip.class);
        {
            src.setClassifier("src");
            src.from(delayedFile(DevConstants.CHANGELOG));
            src.from(delayedFile(DevConstants.FML_LICENSE));
            src.from(delayedFile(DevConstants.FML_CREDITS));
            src.from(delayedFile("{FML_DIR}/install"), new CopyInto(null, "!*.gradle"));
            src.from(delayedFile("{FML_DIR}/install"), (new CopyInto(null, "*.gradle"))
                    .addExpand("version", delayedString("{MC_VERSION_SAFE}-{VERSION}"))
                    .addExpand("mappings", delayedString("{MAPPING_CHANNEL_DOC}_{MAPPING_VERSION}"))
                    .addExpand("name", "fml"));
            src.from(delayedFile("{FML_DIR}/gradlew"));
            src.from(delayedFile("{FML_DIR}/gradlew.bat"));
            src.from(delayedFile("{FML_DIR}/gradle/wrapper"), new CopyInto("gradle/wrapper"));
            src.rename(".+?\\.gradle", "build.gradle");
            src.dependsOn("createChangelog");
            src.setExtension("zip");
        }
        project.getArtifacts().add("archives", src);
    }

    public static String getVersionFromGit(Project project)
    {
        return getVersionFromGit(project, project.getProjectDir());
    }

    public static String getVersionFromGit(Project project, File workDir)
    {
        if (project == null)
        {
            project = BasePlugin.getProject(null, null);
        }

        String fullVersion = runGit(project, workDir, "describe", "--long", "--match=[^(jenkins)]*");
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
            branch = runGit(project, workDir, "rev-parse", "--abbrev-ref", "HEAD");
        }
        else
        {
            branch = System.getenv("GIT_BRANCH");
            branch = branch.substring(branch.lastIndexOf('/') + 1);
        }

        if (branch != null && (branch.equals("master") || branch.equals("HEAD")))
        {
            branch = null;
        }

        StringBuilder out = new StringBuilder();
        out.append(DelayedBase.resolve("{MC_VERSION_SAFE}", project)).append('-'); // Somehow configure this?
        out.append(major).append('.').append(minor).append('.').append(revision).append('.').append(build);
        if (branch != null)
        {
            out.append('-').append(branch);
        }

        return out.toString();
    }

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();

        SubprojectTask task = (SubprojectTask) project.getTasks().getByName("eclipseClean");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());

        task = (SubprojectTask) project.getTasks().getByName("eclipseFML");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
    }
}

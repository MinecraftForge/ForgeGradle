package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.dev.DevConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import net.minecraftforge.gradle.CopyInto;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.tasks.ApplyS2STask;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.ExtractS2SRangeTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.ReplaceJavadocsTask;
import net.minecraftforge.gradle.tasks.abstractutil.DelayedJar;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.abstractutil.FileFilterTask;
import net.minecraftforge.gradle.tasks.dev.ChangelogTask;
import net.minecraftforge.gradle.tasks.dev.FMLVersionPropTask;
import net.minecraftforge.gradle.tasks.dev.ForgeVersionReplaceTask;
import net.minecraftforge.gradle.tasks.dev.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.dev.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.dev.GeneratePatches;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;
import net.minecraftforge.gradle.tasks.dev.SubmoduleChangelogTask;
import net.minecraftforge.gradle.tasks.dev.SubprojectTask;
import net.minecraftforge.gradle.tasks.dev.VersionJsonTask;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Javadoc;

import com.google.common.base.Throwables;

public class ForgeDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set fmlDir
        getExtension().setFmlDir("fml");
        
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
            
            for (File f : project.fileTree(delayedFile(DevConstants.FORGE_RESOURCES).call()).getFiles())
            {
                if(f.getPath().endsWith(".exc"))
                    genSrgTask.addExtraExc(f);
                else if(f.getPath().endsWith(".srg"))
                    genSrgTask.addExtraSrg(f);
            }
        }

        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        createSourceCopyTasks();
        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupForge", DefaultTask.class);
        task.dependsOn("extractForgeSources", "generateProjects", "eclipse", "copyAssets");
        task.setGroup("Forge");

        // the master task.
        task = makeTask("buildPackages");
        task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "packageUserDev", "packageSrc", "genJavadocs");
        task.setGroup("Forge");
    }

    protected void createJarProcessTasks()
    {
        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setOutCleanJar(delayedFile(JAR_SRG_FORGE));
            task2.setSrg(delayedFile(NOTCH_2_SRG_SRG));
            task2.setExceptorCfg(delayedFile(JOINED_EXC));
            task2.setExceptorJson(delayedFile(EXC_JSON));
            task2.addTransformerClean(delayedFile(FML_RESOURCES + "/fml_at.cfg"));
            task2.addTransformerClean(delayedFile(FORGE_RESOURCES + "/forge_at.cfg"));
            task2.setApplyMarkers(true);
            task2.dependsOn("downloadMcpTools", "mergeJars", "genSrgs");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(JAR_SRG_FORGE));
            task3.setOutJar(delayedFile(ZIP_DECOMP_FORGE));
            task3.setFernFlower(delayedFile(Constants.FERNFLOWER));
            task3.setPatch(delayedFile(MCP_PATCH_DIR));
            task3.setAstyleConfig(delayedFile(ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar");
        }

        ProcessSrcJarTask task4 = makeTask("fmlPatchJar", ProcessSrcJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_DECOMP_FORGE));
            task4.setOutJar(delayedFile(ZIP_FMLED_FORGE));
            task4.addStage("fml", delayedFile(FML_PATCH_DIR), delayedFile(FML_SOURCES), delayedFile(FML_RESOURCES), delayedFile("{MAPPINGS_DIR}/patches/Start.java"), delayedFile(DEOBF_DATA), delayedFile(FML_VERSIONF));
            task4.setDoesCache(false);
            task4.setMaxFuzz(2);
            task4.dependsOn("decompile", "compressDeobfData", "createVersionPropertiesFML");
        }
        
        RemapSourcesTask remapTask = makeTask("remapCleanJar", RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(ZIP_FMLED_FORGE));
            remapTask.setOutJar(delayedFile(REMAPPED_CLEAN));
            remapTask.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            remapTask.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            remapTask.setParamsCsv(delayedFile(DevConstants.PARAMS_CSV));
            remapTask.setDoesCache(false);
            remapTask.setNoJavadocs();
            remapTask.dependsOn("fmlPatchJar");
        }

        task4 = makeTask("forgePatchJar", ProcessSrcJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_FMLED_FORGE));
            task4.setOutJar(delayedFile(ZIP_PATCHED_FORGE));
            task4.addStage("forge", delayedFile(FORGE_PATCH_DIR));
            task4.setDoesCache(false);
            task4.setMaxFuzz(2);
            task4.dependsOn("fmlPatchJar");
        }
        
        remapTask = makeTask("remapSourcesJar", RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(ZIP_PATCHED_FORGE));
            remapTask.setOutJar(delayedFile(ZIP_RENAMED_FORGE));
            remapTask.setMethodsCsv(delayedFile(METHODS_CSV));
            remapTask.setFieldsCsv(delayedFile(FIELDS_CSV));
            remapTask.setParamsCsv(delayedFile(PARAMS_CSV));
            remapTask.setDoesCache(false);
            remapTask.setNoJavadocs();
            remapTask.dependsOn("forgePatchJar");
        }
    }

    private void createSourceCopyTasks()
    {
        ExtractTask task = makeTask("extractMcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(REMAPPED_CLEAN));
            task.into(delayedFile(ECLIPSE_CLEAN_RES));
            task.dependsOn("extractWorkspace", "remapCleanJar");
        }

        task = makeTask("extractMcSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(REMAPPED_CLEAN));
            task.into(delayedFile(ECLIPSE_CLEAN_SRC));
            task.dependsOn("extractMcResources");
        }

        task = makeTask("extractForgeResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(ZIP_RENAMED_FORGE));
            task.into(delayedFile(ECLIPSE_FORGE_RES));
            task.dependsOn("remapSourcesJar", "extractWorkspace");
        }

        task = makeTask("extractForgeSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.from(delayedFile(ZIP_RENAMED_FORGE));
            task.into(delayedFile(ECLIPSE_FORGE_SRC));
            task.dependsOn("extractForgeResources");
        }

    }

    @SuppressWarnings("serial")
    private void createProjectTasks()
    {
        FMLVersionPropTask sub = makeTask("createVersionPropertiesFML", FMLVersionPropTask.class);
        {
            //sub.setTasks("createVersionProperties");
            //sub.setBuildFile(delayedFile("{FML_DIR}/build.gradle"));
            sub.setVersion(new Closure<String>(project)
            {
                @Override
                public String call(Object... args)
                {
                    return FmlDevPlugin.getVersionFromGit(project, new File(delayedString("{FML_DIR}").call()));
                }
            });
            sub.setOutputFile(delayedFile(FML_VERSIONF));
        }

        GenDevProjectsTask task = makeTask("generateProjectClean", GenDevProjectsTask.class);
        {
            task.setTargetDir(delayedFile(ECLIPSE_CLEAN));
            task.setJson(delayedFile(JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectForge", GenDevProjectsTask.class);
        {
            task.setJson(delayedFile(JSON_DEV));
            task.setTargetDir(delayedFile(ECLIPSE_FORGE));

            task.addSource(delayedFile(ECLIPSE_FORGE_SRC));
            task.addSource(delayedFile(FORGE_SOURCES));
            task.addTestSource(delayedFile(FORGE_TEST_SOURCES));

            task.addResource(delayedFile(ECLIPSE_FORGE_RES));
            task.addResource(delayedFile(FORGE_RESOURCES));
            task.addTestResource(delayedFile(FORGE_TEST_RES));

            task.dependsOn("extractNatives","createVersionPropertiesFML");
        }

        makeTask("generateProjects").dependsOn("generateProjectClean", "generateProjectForge");
    }

    private void createEclipseTasks()
    {
        SubprojectTask task = makeTask("eclipseClean", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_CLEAN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractMcSource", "generateProjects");
        }

        task = makeTask("eclipseForge", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_FORGE + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractForgeSources", "generateProjects");
        }

        makeTask("eclipse").dependsOn("eclipseClean", "eclipseForge");
    }

    private void createMiscTasks()
    {
        DelayedFile rangeMapClean = delayedFile("{BUILD_DIR}/tmp/rangemapCLEAN.txt");
        DelayedFile rangeMapDirty = delayedFile("{BUILD_DIR}/tmp/rangemapDIRTY.txt");
        
        ExtractS2SRangeTask extractRange = makeTask("extractRangeForge", ExtractS2SRangeTask.class);
        {
            extractRange.setLibsFromProject(delayedFile(ECLIPSE_FORGE + "/build.gradle"), "compile", true);
            extractRange.addIn(delayedFile(ECLIPSE_FORGE_SRC));
            extractRange.setRangeMap(rangeMapDirty);
        }
        
        ApplyS2STask applyS2S = makeTask("retroMapForge", ApplyS2STask.class);
        {
            applyS2S.addIn(delayedFile(ECLIPSE_FORGE_SRC));
            applyS2S.setOut(delayedFile(PATCH_DIRTY));
            applyS2S.addSrg(delayedFile(MCP_2_SRG_SRG));
            applyS2S.addExc(delayedFile(MCP_EXC));
            applyS2S.addExc(delayedFile(SRG_EXC)); // just in case
            applyS2S.setRangeMap(rangeMapDirty);
            applyS2S.dependsOn("genSrgs", extractRange);
        }
        
        extractRange = makeTask("extractRangeClean", ExtractS2SRangeTask.class);
        {
            extractRange.setLibsFromProject(delayedFile(ECLIPSE_CLEAN + "/build.gradle"), "compile", true);
            extractRange.addIn(delayedFile(REMAPPED_CLEAN));
            extractRange.setRangeMap(rangeMapClean);
        }
        
        applyS2S = makeTask("retroMapClean", ApplyS2STask.class);
        {
            applyS2S.addIn(delayedFile(REMAPPED_CLEAN));
            applyS2S.setOut(delayedFile(PATCH_CLEAN));
            applyS2S.addSrg(delayedFile(MCP_2_SRG_SRG));
            applyS2S.addExc(delayedFile(MCP_EXC));
            applyS2S.addExc(delayedFile(SRG_EXC)); // just in case
            applyS2S.setRangeMap(rangeMapClean);
            applyS2S.dependsOn("genSrgs", extractRange);
        }
        
        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(FORGE_PATCH_DIR));
            task2.setOriginal(delayedFile(PATCH_CLEAN)); // was ECLIPSE_CLEAN_SRC
            task2.setChanged(delayedFile(PATCH_DIRTY)); // ECLIPSE_FORGE_SRC
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.dependsOn("retroMapForge", "retroMapClean");
            task2.setGroup("Forge");
        }

        Delete clean = makeTask("cleanForge", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }
        (project.getTasksByName("clean", false).toArray(new Task[0])[0]).dependsOn("cleanForge");

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(MCP_2_NOTCH_SRG));
            obf.setExc(delayedFile(JOINED_EXC));
            obf.setReverse(false);
            obf.setPreFFJar(delayedFile(JAR_SRG_FORGE));
            obf.setOutJar(delayedFile(REOBF_TMP));
            obf.setBuildFile(delayedFile(ECLIPSE_FORGE + "/build.gradle"));
            obf.setMethodsCsv(delayedFile(METHODS_CSV));
            obf.setFieldsCsv(delayedFile(FIELDS_CSV));
            obf.dependsOn("generateProjects", "extractForgeSources", "genSrgs");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(Constants.JAR_MERGED));
            task3.setDirtyJar(delayedFile(REOBF_TMP));
            task3.setDeobfDataLzma(delayedFile(DEOBF_DATA));
            task3.setOutJar(delayedFile(BINPATCH_TMP));
            task3.setSrg(delayedFile(NOTCH_2_SRG_SRG));
            task3.addPatchList(delayedFileTree(FORGE_PATCH_DIR));
            task3.addPatchList(delayedFileTree(FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData");
        }

        ForgeVersionReplaceTask task4 = makeTask("ciWriteBuildNumber", ForgeVersionReplaceTask.class);
        {
            task4.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            task4.setOutputFile(delayedFile(FORGE_VERSION_JAVA));
            task4.setReplacement(delayedString("{BUILD_NUM}"));
        }

        SubmoduleChangelogTask task5 = makeTask("fmlChangelog", SubmoduleChangelogTask.class);
        {
            task5.setSubmodule(delayedFile("fml"));
            task5.setModuleName("FML");
            task5.setPrefix("MinecraftForge/FML");
            task5.setOutputFile(project.file("changelog.txt"));
        }
    }

    @SuppressWarnings("serial")
    private void createPackageTasks()
    {
        ChangelogTask log = makeTask("createChangelog", ChangelogTask.class);
        {
            log.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            log.setServerRoot(delayedString("{JENKINS_SERVER}"));
            log.setJobName(delayedString("{JENKINS_JOB}"));
            log.setAuthName(delayedString("{JENKINS_AUTH_NAME}"));
            log.setAuthPassword(delayedString("{JENKINS_AUTH_PASSWORD}"));
            log.setTargetBuild(delayedString("{BUILD_NUM}"));
            log.setOutput(delayedFile(CHANGELOG));
        }

        VersionJsonTask vjson = makeTask("generateVersionJson", VersionJsonTask.class);
        {
            vjson.setInput(delayedFile(INSTALL_PROFILE));
            vjson.setOutput(delayedFile(VERSION_JSON));
            vjson.dependsOn("generateInstallJson");
        }

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        {
            uni.setClassifier("universal");
            uni.getInputs().file(delayedFile(JSON_REL));
            uni.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            uni.from(delayedZipTree(BINPATCH_TMP));
            uni.from(delayedFileTree(FML_RESOURCES));
            uni.from(delayedFileTree(FORGE_RESOURCES));
            uni.from(delayedFile(FML_VERSIONF));
            uni.from(delayedFile(FML_LICENSE));
            uni.from(delayedFile(FML_CREDITS));
            uni.from(delayedFile(FORGE_LICENSE));
            uni.from(delayedFile(FORGE_CREDITS));
            uni.from(delayedFile(PAULSCODE_LISCENCE1));
            uni.from(delayedFile(PAULSCODE_LISCENCE2));
            uni.from(delayedFile(DEOBF_DATA));
            uni.from(delayedFile(CHANGELOG));
            uni.from(delayedFile(VERSION_JSON));
            uni.exclude("devbinpatches.pack.lzma");
            uni.setIncludeEmptyDirs(false);
            uni.setManifest(new Closure<Object>(project)
            {
                public Object call()
                {
                    Manifest mani = (Manifest) getDelegate();
                    mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
                    mani.getAttributes().put("TweakClass", delayedString("{FML_TWEAK_CLASS}").call());
                    mani.getAttributes().put("Class-Path", getServerClassPath(delayedFile(JSON_REL).call()));
                    return null;
                }
            });
            uni.doLast(new Action<Task>()
            {
                @Override
                public void execute(Task arg0)
                {
                    try
                    {
                        signJar(((DelayedJar)arg0).getArchivePath(), "forge", "*/*/**", "!paulscode/**");
                    }
                    catch (Exception e)
                    {
                        Throwables.propagate(e);
                    }
                }
            });
            uni.setDestinationDir(delayedFile("{BUILD_DIR}/distributions").call());
            uni.dependsOn("genBinPatches", "createChangelog", "createVersionPropertiesFML", "generateVersionJson");
        }
        project.getArtifacts().add("archives", uni);

        FileFilterTask task = makeTask("generateInstallJson", FileFilterTask.class);
        {
            task.setInputFile(delayedFile(JSON_REL));
            task.setOutputFile(delayedFile(INSTALL_PROFILE));
            task.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            task.addReplacement("@version@", delayedString("{VERSION}"));
            task.addReplacement("@project@", delayedString("Forge"));
            task.addReplacement("@artifact@", delayedString("net.minecraftforge:forge:{MC_VERSION_SAFE}-{VERSION}"));
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
            inst.from(delayedFile(FML_CREDITS));
            inst.from(delayedFile(FORGE_LICENSE));
            inst.from(delayedFile(FORGE_CREDITS));
            inst.from(delayedFile(PAULSCODE_LISCENCE1));
            inst.from(delayedFile(PAULSCODE_LISCENCE2));
            inst.from(delayedFile(FORGE_LOGO));
            inst.from(delayedZipTree(INSTALLER_BASE), new CopyInto("", "!*.json", "!*.png"));
            inst.dependsOn("packageUniversal", "downloadBaseInstaller", "generateInstallJson");
            inst.rename("forge_logo\\.png", "big_logo.png");
            inst.setExtension("jar");
        }
        project.getArtifacts().add("archives", inst);

        final Zip patchZipFML = makeTask("zipFmlPatches", Zip.class);
        {
            patchZipFML.from(delayedFile(FML_PATCH_DIR));
            patchZipFML.setArchiveName("fmlpatches.zip");
            patchZipFML.setDestinationDir(delayedFile("{BUILD_DIR}/tmp/").call());
        }

        final Zip patchZipForge = makeTask("zipForgePatches", Zip.class);
        {
            patchZipForge.from(delayedFile(FORGE_PATCH_DIR));
            patchZipForge.setArchiveName("forgepatches.zip");
            patchZipForge.setDestinationDir(delayedFile("{BUILD_DIR}/tmp/").call());
        }

        final Zip classZip = makeTask("jarClasses", Zip.class);
        {
            classZip.from(delayedZipTree(BINPATCH_TMP), new CopyInto("", "**/*.class"));
            classZip.setArchiveName("binaries.jar");
            classZip.setDestinationDir(delayedFile("{BUILD_DIR}/tmp/").call());
        }

        final File javadocSource = project.file(delayedFile("{BUILD_DIR}/tmp/javadocSource"));
        ReplaceJavadocsTask jdSource = makeTask("replaceJavadocs", ReplaceJavadocsTask.class);
        {
            jdSource.from(delayedFile(FML_SOURCES));
            jdSource.from(delayedFile(FORGE_SOURCES));
            jdSource.from(delayedFile(ECLIPSE_FORGE_SRC));
            jdSource.setOutFile(delayedFile("{BUILD_DIR}/tmp/javadocSource"));
            jdSource.setMethodsCsv(delayedFile(METHODS_CSV));
            jdSource.setFieldsCsv(delayedFile(FIELDS_CSV));
        }

        final File javadoc_temp = project.file(delayedFile("{BUILD_DIR}/tmp/javadoc"));
        final SubprojectTask javadocJar = makeTask("genJavadocs", SubprojectTask.class);
        {
            javadocJar.dependsOn("replaceJavadocs");
            javadocJar.setBuildFile(delayedFile(ECLIPSE_FORGE + "/build.gradle"));
            javadocJar.setTasks("javadoc");
            javadocJar.setConfigureTask(new Action<Task>() {
                public void execute(Task obj)
                {
                    Javadoc task = (Javadoc)obj;
                    task.setSource(project.fileTree(javadocSource));
                    task.setDestinationDir(javadoc_temp);
                }
            });
            javadocJar.dependsOn("generateProjects", "extractForgeSources");
        }

        final Zip javadoc = makeTask("packageJavadoc", Zip.class);
        {
            javadoc.from(javadoc_temp);
            javadoc.setClassifier("javadoc");
            javadoc.dependsOn("genJavadocs");
        }
        project.getArtifacts().add("archives", javadoc);
        
        ExtractS2SRangeTask range = makeTask("userDevExtractRange", ExtractS2SRangeTask.class);
        {
            range.setLibsFromProject(delayedFile(DevConstants.ECLIPSE_FORGE + "/build.gradle"), "compile", true);
            range.addIn(delayedFile(DevConstants.FML_SOURCES));
            range.addIn(delayedFile(DevConstants.FORGE_SOURCES));
            range.setRangeMap(delayedFile(DevConstants.USERDEV_RANGEMAP));
            range.dependsOn("extractForgeSources", "generateProjects");
        }
        
        ApplyS2STask s2s = makeTask("userDevSrgSrc", ApplyS2STask.class);
        {
            s2s.addIn(delayedFile(DevConstants.FORGE_SOURCES));
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
            
            // find all the exc & srg files in the resources.
            for (File f : project.fileTree(delayedFile(DevConstants.FORGE_RESOURCES).call()).getFiles())
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
            userDev.from(delayedFile(JSON_DEV));
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return patchZipFML.getArchivePath();
                }
            });
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return patchZipForge.getArchivePath();
                }
            });
            userDev.from(new Closure<File>(project) {
                public File call()
                {
                    return classZip.getArchivePath();
                }
            });
            userDev.from(delayedFile(CHANGELOG));
            userDev.from(delayedZipTree(BINPATCH_TMP), new CopyInto("", "devbinpatches.pack.lzma"));
            userDev.from(delayedFileTree("{FML_DIR}/src/main/resources"), new CopyInto("src/main/resources"));
            userDev.from(delayedFileTree("src/main/resources"), new CopyInto("src/main/resources"));
            userDev.from(delayedZipTree(DevConstants.USERDEV_SRG_SRC), new CopyInto("src/main/java"));
            userDev.from(delayedFile(DEOBF_DATA), new CopyInto("src/main/resources/"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}"), new CopyInto("conf", "astyle.cfg", "exceptor.json", "*.csv", "!packages.csv"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}/patches"), new CopyInto("conf"));
            userDev.from(delayedFile(MERGE_CFG), new CopyInto("conf"));
            userDev.from(delayedFile(NOTCH_2_SRG_SRG), new CopyInto("conf"));
            userDev.from(delayedFile(SRG_EXC), new CopyInto("conf"));
            userDev.from(delayedFile(FML_VERSIONF), new CopyInto("src/main/resources"));
            userDev.rename(".+-dev\\.json", "dev.json");
            userDev.rename(".+?\\.srg", "packaged.srg");
            userDev.rename(".+?\\.exc", "packaged.exc");
            userDev.setIncludeEmptyDirs(false);
            userDev.dependsOn("packageUniversal", "zipFmlPatches", "zipForgePatches", "jarClasses", "createVersionPropertiesFML", s2s);
            userDev.setExtension("jar");
        }
        project.getArtifacts().add("archives", userDev);

        Zip src = makeTask("packageSrc", Zip.class);
        {
            src.setClassifier("src");
            src.from(delayedFile(CHANGELOG));
            src.from(delayedFile(FML_LICENSE));
            src.from(delayedFile(FML_CREDITS));
            src.from(delayedFile(FORGE_LICENSE));
            src.from(delayedFile(FORGE_CREDITS));
            src.from(delayedFile("{FML_DIR}/install"), new CopyInto(null, "!*.gradle"));
            src.from(delayedFile("{FML_DIR}/install"), (new CopyInto(null, "*.gradle")).addExpand("version", delayedString("{MC_VERSION_SAFE}-{VERSION}")).addExpand("name", "forge"));
            src.from(delayedFile("{FML_DIR}/gradlew"));
            src.from(delayedFile("{FML_DIR}/gradlew.bat"));
            src.from(delayedFile("{FML_DIR}/gradle/wrapper"), new CopyInto("gradle/wrapper"));
            src.rename(".+?\\.gradle", "build.gradle");
            src.dependsOn("createChangelog");
            src.setExtension("zip");
        }
        project.getArtifacts().add("archives", src);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static String getVersionFromJava(Project project, String file) throws IOException
    {
        String major = "0";
        String minor = "0";
        String revision = "0";
        String build = "0";

        String prefix = "public static final int";
        List<String> lines = (List<String>)FileUtils.readLines(project.file(file));
        for (String s : lines)
        {
            s = s.trim();
            if (s.startsWith(prefix))
            {
                s = s.substring(prefix.length(), s.length() - 1);
                s = s.replace('=', ' ').replace("Version", "").replaceAll(" +", " ").trim();
                String[] pts = s.split(" ");

                if (pts[0].equals("major")) major = pts[pts.length - 1];
                else if (pts[0].equals("minor")) minor = pts[pts.length - 1];
                else if (pts[0].equals("revision")) revision = pts[pts.length - 1];
            }
        }

        if (System.getenv().containsKey("BUILD_NUMBER"))
        {
            build = System.getenv("BUILD_NUMBER");
        }

        String branch = null;
        if (!System.getenv().containsKey("GIT_BRANCH"))
        {
            branch = runGit(project, project.getProjectDir(), "rev-parse", "--abbrev-ref", "HEAD");
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

        IDelayedResolver resolver = (IDelayedResolver)project.getPlugins().findPlugin("forgedev");
        StringBuilder out = new StringBuilder();

        out.append(DelayedBase.resolve("{MC_VERSION_SAFE}", project, resolver)).append('-'); // Somehow configure this?
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
        
        task = (SubprojectTask) project.getTasks().getByName("eclipseForge");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
        
        task = (SubprojectTask) project.getTasks().getByName("genJavadocs");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
    }
}

package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.dev.DevConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.minecraftforge.gradle.CopyInto;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.ReplaceJavadocsTask;
import net.minecraftforge.gradle.tasks.abstractutil.DelayedJar;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.abstractutil.FileFilterTask;
import net.minecraftforge.gradle.tasks.dev.FMLVersionPropTask;
import net.minecraftforge.gradle.tasks.dev.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.dev.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.dev.GeneratePatches;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;
import net.minecraftforge.gradle.tasks.dev.SubprojectTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.Delete;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.javadoc.Javadoc;

public class McpcDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set folders
        getExtension().setFmlDir("forge/fml");
        getExtension().setForgeDir("forge");
        getExtension().setBukkitDir("bukkit");
        
        createJarProcessTasks();
        createProjectTasks();
        createEclipseTasks();
        createMiscTasks();
        createSourceCopyTasks();
        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupMcpc", DefaultTask.class);
        task.dependsOn("extractMcpcSources", "generateProjects", "eclipse", "copyAssets");
        task.setGroup("MCPC");
        
        // clean packages
        {
            Delete del = makeTask("cleanPackages", Delete.class);
            del.delete("build/distributions");
        }

//        // the master task.
        task = makeTask("buildPackages");
        //task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "genJavadocs");
        task.dependsOn("cleanPackages", "packageUniversal", "packageInstaller", "genJavadocs");
        task.setGroup("MCPC");
    }
    
    @Override
    protected final String getDevJson()
    {
        return DelayedBase.resolve(DevConstants.MCPC_JSON_DEV, project, this);
    }
    
    protected void createJarProcessTasks()
    {
        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setOutCleanJar(delayedFile(JAR_SRG_MCPC));
            task2.setSrg(delayedFile(JOINED_SRG));
            task2.setExceptorCfg(delayedFile(JOINED_EXC));
            task2.setExceptorJson(delayedFile(EXC_JSON));
            task2.addTransformerClean(delayedFile(FML_RESOURCES + "/fml_at.cfg"));
            task2.addTransformerClean(delayedFile(FORGE_RESOURCES + "/forge_at.cfg"));
            task2.setApplyMarkers(true);
            task2.dependsOn("downloadMcpTools", "mergeJars");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(JAR_SRG_MCPC));
            task3.setOutJar(delayedFile(ZIP_DECOMP_MCPC));
            task3.setFernFlower(delayedFile(Constants.FERNFLOWER));
            task3.setPatch(delayedFile(MCP_PATCH_DIR));
            task3.setAstyleConfig(delayedFile(ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar");
        }

        PatchJarTask task4 = makeTask("fmlPatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_DECOMP_MCPC));
            task4.setOutJar(delayedFile(ZIP_FMLED_MCPC));
            task4.setInPatches(delayedFile(FML_PATCH_DIR));
            task4.setDoesCache(true);
            task4.setMaxFuzz(2);
            task4.dependsOn("decompile");
        }

        // add fml sources
        Zip task5 = makeTask("fmlInjectJar", Zip.class);
        {
            task5.from(delayedFileTree(FML_SOURCES));
            task5.from(delayedFileTree(FML_RESOURCES));
            task5.from(delayedZipTree(ZIP_FMLED_MCPC));
            task5.from(delayedFile("{MAPPINGS_DIR}/patches"), new CopyInto("", "Start.java"));
            task5.from(delayedFile(DEOBF_DATA));
            task5.from(delayedFile(FML_VERSIONF));

            // see ZIP_INJECT_MCPC
            task5.setArchiveName("minecraft_fmlinjected.zip");
            task5.setDestinationDir(delayedFile("{BUILD_DIR}/mcpcTmp").call());

            task5.dependsOn("fmlPatchJar", "compressDeobfData", "createVersionPropertiesFML");
        }

        RemapSourcesTask task6 = makeTask("remapSourcesJar", RemapSourcesTask.class);
        {
            task6.setInJar(delayedFile(ZIP_INJECT_MCPC));
            task6.setOutJar(delayedFile(ZIP_RENAMED_MCPC));
            task6.setMethodsCsv(delayedFile(METHODS_CSV));
            task6.setFieldsCsv(delayedFile(FIELDS_CSV));
            task6.setParamsCsv(delayedFile(PARAMS_CSV));
            task6.setDoesCache(true);
            task6.setDoesJavadocs(false);
            task6.dependsOn("fmlInjectJar");
        }
        
        task5 = makeTask("forgeInjectJar", Zip.class);
        {
            task5.from(delayedFileTree(FORGE_SOURCES));
            task5.from(delayedFileTree(FORGE_RESOURCES));
            task5.from(delayedFileTree(BUKKIT_SOURCES));
            task5.from(delayedFileTree(BUKKIT_RESOURCES));
            task5.from(delayedZipTree(ZIP_RENAMED_MCPC));

            // see ZIP_FINJECT_MCPC
            task5.setArchiveName("minecraft_forgeinjected.zip");
            task5.setDestinationDir(delayedFile("{BUILD_DIR}/mcpcTmp").call());

            task5.dependsOn("remapSourcesJar");
        }

        task4 = makeTask("forgePatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_FINJECT_MCPC));
            task4.setOutJar(delayedFile(ZIP_FORGED_MCPC));
            task4.setInPatches(delayedFile(FORGE_PATCH_DIR));
            task4.setDoesCache(true);
            task4.setMaxFuzz(2);
            task4.dependsOn("forgeInjectJar");
        }
         
        task4 = makeTask("mcpcPatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_FORGED_MCPC));
            task4.setOutJar(delayedFile(ZIP_PATCHED_MCPC));
            task4.setInPatches(delayedFile(MCPC_PATCH_DIR));
            task4.setDoesCache(false);
            task4.setMaxFuzz(2);
            task4.dependsOn("forgePatchJar");
        }
    }
    
    private void createSourceCopyTasks()
    {
        ExtractTask task = makeTask("extractCleanResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_FORGED_MCPC));
            task.into(delayedFile(ECLIPSE_CLEAN_RES));
            task.dependsOn("extractWorkspace", "forgePatchJar");
        }

        task = makeTask("extractCleanSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_FORGED_MCPC));
            task.into(delayedFile(ECLIPSE_CLEAN_SRC));
            task.dependsOn("extractCleanResources");
        }

        task = makeTask("extractMcpcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_MCPC));
            task.into(delayedFile(ECLIPSE_MCPC_RES));
            task.dependsOn("mcpcPatchJar", "extractWorkspace");
        }

        task = makeTask("extractMcpcSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_MCPC));
            task.into(delayedFile(ECLIPSE_MCPC_SRC));
            task.dependsOn("extractMcpcResources");
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
        
        ExtractTask extract = makeTask("extractRes", ExtractTask.class);
        {
            extract.into(delayedFile(EXTRACTED_RES));
            for (File f : delayedFile("src/main").call().listFiles())
            {
                if (f.isDirectory())
                    continue;
                String path = f.getAbsolutePath();
                if (path.endsWith(".jar") || path.endsWith(".zip"))
                    extract.from(delayedFile(path));
            }
        }

        GenDevProjectsTask task = makeTask("generateProjectClean", GenDevProjectsTask.class);
        {
            task.setTargetDir(delayedFile(ECLIPSE_CLEAN));
            task.setJson(delayedFile(MCPC_JSON_DEV)); // Change to FmlConstants.JSON_BASE eventually, so that it's the base vanilla json
            
            task.addSource(delayedFile(ECLIPSE_CLEAN_SRC));
            
            task.addResource(delayedFile(ECLIPSE_CLEAN_RES));
            
            task.dependsOn("extractNatives");
        }

        task = makeTask("generateProjectMcpc", GenDevProjectsTask.class);
        {
            task.setJson(delayedFile(MCPC_JSON_DEV));
            task.setTargetDir(delayedFile(ECLIPSE_MCPC));

            task.addSource(delayedFile(ECLIPSE_MCPC_SRC));
            task.addSource(delayedFile(MCPC_SOURCES));

            task.addResource(delayedFile(ECLIPSE_MCPC_RES));
            task.addResource(delayedFile(MCPC_RESOURCES));
            task.addResource(delayedFile(EXTRACTED_RES));

            task.dependsOn("extractRes", "extractNatives","createVersionPropertiesFML");
        }

        makeTask("generateProjects").dependsOn("generateProjectClean", "generateProjectMcpc");
    }
    
    private void createEclipseTasks()
    {
        SubprojectTask task = makeTask("eclipseClean", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_CLEAN + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractCleanSource", "generateProjects");
        }

        task = makeTask("eclipseMcpc", SubprojectTask.class);
        {
            task.setBuildFile(delayedFile(ECLIPSE_MCPC + "/build.gradle"));
            task.setTasks("eclipse");
            task.dependsOn("extractMcpcSources", "generateProjects");
        }

        makeTask("eclipse").dependsOn("eclipseClean", "eclipseMcpc");
    }
    
    private void createMiscTasks()
    {
        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(MCPC_PATCH_DIR));
            task2.setOriginalDir(delayedFile(ECLIPSE_CLEAN_SRC));
            task2.setChangedDir(delayedFile(ECLIPSE_MCPC_SRC));
            task2.setOriginalPrefix("../src-base/minecraft");
            task2.setChangedPrefix("../src-work/minecraft");
            task2.setGroup("MCPC");
        }

        Delete clean = makeTask("cleanMcpc", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }
        (project.getTasksByName("clean", false).toArray(new Task[0])[0]).dependsOn("cleanMcpc");

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(MCP_2_NOTCH_SRG));
            obf.setExc(delayedFile(JOINED_EXC));
            obf.setReverse(false);
            obf.setPreFFJar(delayedFile(JAR_SRG_MCPC));
            obf.setOutJar(delayedFile(REOBF_TMP));
            obf.setBuildFile(delayedFile(ECLIPSE_MCPC + "/build.gradle"));
            obf.setMethodsCsv(delayedFile(METHODS_CSV));
            obf.setFieldsCsv(delayedFile(FIELDS_CSV));
            obf.dependsOn("generateProjects", "extractMcpcSources", "genSrgs");
        }

        GenBinaryPatches task3 = makeTask("genBinPatches", GenBinaryPatches.class);
        {
            task3.setCleanClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task3.setCleanServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task3.setCleanMerged(delayedFile(Constants.JAR_MERGED));
            task3.setDirtyJar(delayedFile(REOBF_TMP));
            task3.setDeobfDataLzma(delayedFile(DEOBF_DATA));
            task3.setOutJar(delayedFile(BINPATCH_TMP));
            task3.setSrg(delayedFile(JOINED_SRG));
            task3.addPatchList(delayedFileTree(MCPC_PATCH_DIR));
            task3.addPatchList(delayedFileTree(FORGE_PATCH_DIR));
            task3.addPatchList(delayedFileTree(FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData");
        }

        /*
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
        }
        */
    }
    
    @SuppressWarnings("serial")
    private void createPackageTasks()
    {
        /*
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
        */

        final DelayedJar uni = makeTask("packageUniversal", DelayedJar.class);
        {
            uni.setClassifier(delayedString("B{BUILD_NUM}").call());
            uni.getInputs().file(delayedFile(MCPC_JSON_REL));
            uni.getOutputs().upToDateWhen(Constants.CALL_FALSE);
            uni.from(delayedZipTree(BINPATCH_TMP));
            uni.from(delayedFileTree(FML_RESOURCES));
            uni.from(delayedFileTree(FORGE_RESOURCES));
            uni.from(delayedFileTree(MCPC_RESOURCES));
            uni.from(delayedFileTree(EXTRACTED_RES));
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
                    mani.getAttributes().put("Class-Path", getServerClassPath(delayedFile(MCPC_JSON_REL).call()));
                    return null;
                }
            });
            
//            uni.doLast(new Action<Task>()
//            {
//                @Override
//                public void execute(Task arg0)
//                {
//                    try
//                    {
//                        signJar(((DelayedJar)arg0).getArchivePath(), "forge", "*/*/**", "!paulscode/**");
//                    }
//                    catch (Exception e)
//                    {
//                        Throwables.propagate(e);
//                    }
//                }
//            });
            
            uni.setDestinationDir(delayedFile("{BUILD_DIR}/distributions").call());
            //uni.dependsOn("genBinPatches", "createChangelog", "createVersionPropertiesFML", "generateVersionJson");
            uni.dependsOn("genBinPatches", "createVersionPropertiesFML");
        }
        project.getArtifacts().add("archives", uni);

        FileFilterTask task = makeTask("generateInstallJson", FileFilterTask.class);
        {
            task.setInputFile(delayedFile(MCPC_JSON_REL));
            task.setOutputFile(delayedFile(INSTALL_PROFILE));
            task.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            task.addReplacement("@version@", delayedString("{VERSION}"));
            task.addReplacement("@project@", delayedString("mcpc-plus"));
            task.addReplacement("@artifact@", delayedString("net.minecraftforge:forge:{MC_VERSION}-{VERSION}"));
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

        final File javadocSource = project.file(delayedFile("{BUILD_DIR}/tmp/javadocSource"));
        ReplaceJavadocsTask jdSource = makeTask("replaceJavadocs", ReplaceJavadocsTask.class);
        {
            jdSource.from(delayedFile(MCPC_SOURCES));
            jdSource.from(delayedFile(ECLIPSE_MCPC_SRC));
            jdSource.setOutFile(delayedFile("{BUILD_DIR}/tmp/javadocSource"));
            jdSource.setMethodsCsv(delayedFile(METHODS_CSV));
            jdSource.setFieldsCsv(delayedFile(FIELDS_CSV));
        }

        final File javadoc_temp = project.file(delayedFile("{BUILD_DIR}/tmp/javadoc"));
        final SubprojectTask javadocJar = makeTask("genJavadocs", SubprojectTask.class);
        {
            javadocJar.dependsOn("replaceJavadocs");
            javadocJar.setBuildFile(delayedFile(ECLIPSE_MCPC + "/build.gradle"));
            javadocJar.setTasks("javadoc");
            javadocJar.setConfigureTask(new Action<Task>() {
                public void execute(Task obj)
                {
                    Javadoc task = (Javadoc)obj;
                    task.setSource(project.fileTree(javadocSource));
                    task.setDestinationDir(javadoc_temp);
                }
            });
        }
    }
    
    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        SubprojectTask task = (SubprojectTask) project.getTasks().getByName("eclipseClean");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
        
        task = (SubprojectTask) project.getTasks().getByName("eclipseMcpc");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
        
        task = (SubprojectTask) project.getTasks().getByName("genJavadocs");
        task.configureProject(getExtension().getSubprojects());
        task.configureProject(getExtension().getCleanProject());
    }
}

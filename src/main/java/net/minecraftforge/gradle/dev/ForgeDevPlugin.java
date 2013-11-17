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
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.DelayedJar;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.abstractutil.FileFilterTask;
import net.minecraftforge.gradle.tasks.dev.ChangelogTask;
import net.minecraftforge.gradle.tasks.dev.GenBinaryPatches;
import net.minecraftforge.gradle.tasks.dev.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.dev.GeneratePatches;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;
import net.minecraftforge.gradle.tasks.dev.SubprojectTask;

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
        //task.dependsOn("launch4j", "packageUniversal", "createChangelog", "packageInstaller");
        task.dependsOn("createChangelog", "packageUniversal", "packageInstaller", "packageInstaller", "packageJavadoc");
        task.setGroup("Forge");
    }

    protected void createJarProcessTasks()
    {

        ProcessJarTask task2 = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            task2.setInJar(delayedFile(Constants.JAR_MERGED));
            task2.setExceptorJar(delayedFile(Constants.EXCEPTOR));
            task2.setOutCleanJar(delayedFile(JAR_SRG_FORGE));
            task2.setSrg(delayedFile(PACKAGED_SRG));
            task2.setExceptorCfg(delayedFile(PACKAGED_EXC));
            task2.addTransformer(delayedFile(FML_RESOURCES + "/fml_at.cfg"));
            task2.addTransformer(delayedFile(FORGE_RESOURCES + "/forge_at.cfg"));
            task2.dependsOn("downloadMcpTools", "fixMappings", "mergeJars");
        }

        DecompileTask task3 = makeTask("decompile", DecompileTask.class);
        {
            task3.setInJar(delayedFile(JAR_SRG_FORGE));
            task3.setOutJar(delayedFile(ZIP_DECOMP_FORGE));
            task3.setFernFlower(delayedFile(Constants.FERNFLOWER));
            task3.setPatch(delayedFile(PACKAGED_PATCH));
            task3.setAstyleConfig(delayedFile(ASTYLE_CFG));
            task3.dependsOn("downloadMcpTools", "deobfuscateJar", "fixMappings");
        }

        PatchJarTask task4 = makeTask("fmlPatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_DECOMP_FORGE));
            task4.setOutJar(delayedFile(ZIP_FMLED_FORGE));
            task4.setInPatches(delayedFile(FML_PATCH_DIR));
            task4.setDoesCache(false);
            task4.dependsOn("decompile");
        }
        
        // add fml sources
        Zip task5 = makeTask("fmlInjectJar", Zip.class);
        {
            task5.from(delayedFileTree(FML_SOURCES));
            task5.from(delayedFileTree(FML_RESOURCES));
            task5.from(delayedZipTree(ZIP_FMLED_FORGE));
            task5.from(delayedFile("{MAPPINGS_DIR}/patches"), new CopyInto("", "Start.java"));
            task5.from(delayedFile(DEOBF_DATA));
            task5.from(delayedFile(FML_VERSIONF));
            
            // see ZIP_INJECT_FORGE
            task5.setArchiveName("minecraft_fmlinjected.zip");
            task5.setDestinationDir(delayedFile("{BUILD_DIR}/forgeTmp").call());
            
            task5.dependsOn("fmlPatchJar", "compressDeobfData", "createVersionPropertiesFML");
        }
        
        RemapSourcesTask task6 = makeTask("remapSourcesJar", RemapSourcesTask.class);
        {
            task6.setInJar(delayedFile(ZIP_INJECT_FORGE));
            task6.setOutJar(delayedFile(ZIP_RENAMED_FORGE));
            task6.setMethodsCsv(delayedFile(METHODS_CSV));
            task6.setFieldsCsv(delayedFile(FIELDS_CSV));
            task6.setParamsCsv(delayedFile(PARAMS_CSV));
            task6.setDoesCache(false);
            task6.dependsOn("fmlInjectJar");
        }
        
        task4 = makeTask("forgePatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_RENAMED_FORGE));
            task4.setOutJar(delayedFile(ZIP_PATCHED_FORGE));
            task4.setInPatches(delayedFile(FORGE_PATCH_DIR));
            task4.setDoesCache(false);
            task4.dependsOn("remapSourcesJar");
        }
    }

    private void createSourceCopyTasks()
    {
        ExtractTask task = makeTask("extractMcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_RENAMED_FORGE));
            task.into(delayedFile(ECLIPSE_CLEAN + "/src/main/resources"));
            task.dependsOn("extractWorkspace", "remapSourcesJar");
        }

        task = makeTask("extractMcSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_RENAMED_FORGE));
            task.into(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
            task.dependsOn("extractMcResources");
        }

        task = makeTask("extractForgeResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_FORGE));
            task.into(delayedFile(ECLIPSE_FORGE + "/src/resources"));
            task.dependsOn("forgePatchJar", "extractWorkspace");
        }

        task = makeTask("extractForgeSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_FORGE));
            task.into(delayedFile(ECLIPSE_FORGE + "/src/minecraft"));
            task.dependsOn("extractForgeResources");
        }

    }

    private void createProjectTasks()
    {
        SubprojectTask sub = makeTask("createVersionPropertiesFML", SubprojectTask.class);
        {
            sub.setTasks("createVersionProperties");
            sub.setBuildFile(delayedFile("{FML_DIR}/build.gradle"));
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

            task.addSource(delayedFile(ECLIPSE_FORGE + "/src/minecraft"));
            task.addSource(delayedFile(FORGE_SOURCES));

            task.addResource(delayedFile(ECLIPSE_FORGE + "/src/resources"));
            task.addResource(delayedFile(FORGE_RESOURCES));

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
        GeneratePatches task2 = makeTask("genPatches", GeneratePatches.class);
        {
            task2.setPatchDir(delayedFile(FORGE_PATCH_DIR));
            task2.setOriginalDir(delayedFile(ECLIPSE_CLEAN + "/src/main/java"));
            task2.setChangedDir(delayedFile(ECLIPSE_FORGE + "/src/minecraft"));
            task2.setOriginalPrefix("../src_base/minecraft");
            task2.setChangedPrefix("../src_work/minecraft");
            task2.setGroup("Forge");
        }

        Delete clean = makeTask("cleanForge", Delete.class);
        {
            clean.delete("eclipse");
            clean.setGroup("Clean");
        }
        project.task("clean").dependsOn("cleanForge");

        ObfuscateTask obf = makeTask("obfuscateJar", ObfuscateTask.class);
        {
            obf.setSrg(delayedFile(MCP_2_NOTCH_SRG));
            obf.setReverse(true);
            obf.setOutJar(delayedFile(REOBF_TMP));
            obf.setBuildFile(delayedFile(ECLIPSE_FORGE + "/build.gradle"));
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
            task3.setSrg(delayedFile(PACKAGED_SRG));
            task3.addPatchList(delayedFileTree(FORGE_PATCH_DIR));
            task3.addPatchList(delayedFileTree(FML_PATCH_DIR));
            task3.dependsOn("obfuscateJar", "compressDeobfData", "fixMappings");
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
            uni.exclude("devbinpatches.pack.lzma");
            uni.setIncludeEmptyDirs(false);
            uni.setManifest(new Closure<Object>(project)
            {
                public Object call()
                {
                    Manifest mani = (Manifest) getDelegate();
                    mani.getAttributes().put("Main-Class", delayedString("{MAIN_CLASS}").call());
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
            uni.dependsOn("genBinPatches", "createChangelog", "createVersionPropertiesFML");
        }
        project.getArtifacts().add("archives", uni);

        FileFilterTask task = makeTask("generateInstallJson", FileFilterTask.class);
        {
            task.setInputFile(delayedFile(JSON_REL));
            task.setOutputFile(delayedFile(INSTALL_PROFILE));
            task.addReplacement("@minecraft_version@", delayedString("{MC_VERSION}"));
            task.addReplacement("@version@", delayedString("{VERSION}"));
            task.addReplacement("@project@", delayedString("Forge"));
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
            uni.from(delayedFile(FORGE_LICENSE));
            uni.from(delayedFile(FORGE_CREDITS));
            uni.from(delayedFile(PAULSCODE_LISCENCE1));
            uni.from(delayedFile(PAULSCODE_LISCENCE2));
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

        final SubprojectTask javadocJar = makeTask("genJavadocs", SubprojectTask.class);
        {
            javadocJar.setBuildFile(delayedFile(ECLIPSE_FORGE + "/build.gradle"));
            javadocJar.setTasks("javadoc");
            javadocJar.setConfigureTask(new Closure<Object>(this, null) {
                public Object call(Object obj)
                {
                    Javadoc task = (Javadoc)obj;
                    task.setDestinationDir(delayedFile(JAVADOC_TMP).call());
                    return null;
                }
            });
        }

        final Zip javadoc = makeTask("packageJavadoc", Zip.class);
        {
            javadoc.from(delayedFile(JAVADOC_TMP));
            javadoc.setClassifier("javadoc");
            javadoc.dependsOn("genJavadocs");
        }
        project.getArtifacts().add("archives", javadoc);

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
            userDev.from(delayedFileTree("{FML_DIR}/src"), new CopyInto("src"));
            userDev.from(delayedFileTree("src"), new CopyInto("src"));
            userDev.from(delayedFileTree(MERGE_CFG), new CopyInto("conf"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}"), new CopyInto("conf", "astyle.cfg"));
            userDev.from(delayedFileTree("{MAPPINGS_DIR}"), new CopyInto("mappings", "*.csv", "!packages.csv"));
            userDev.from(delayedFile(PACKAGED_SRG), new CopyInto("conf"));
            userDev.from(delayedFile(PACKAGED_EXC), new CopyInto("conf"));
            userDev.from(delayedFile(PACKAGED_PATCH), new CopyInto("conf"));
            userDev.from(delayedFile(FML_VERSIONF), new CopyInto("src/main/resources"));
            userDev.rename(".+?\\.json", "dev.json");
            userDev.rename(".+?\\.srg", "packaged.srg");
            userDev.rename(".+?\\.exc", "packaged.exc");
            userDev.rename(".+?\\.patch", "packaged.patch");
            userDev.setIncludeEmptyDirs(false);
            userDev.dependsOn("packageUniversal", "zipFmlPatches", "zipForgePatches", "jarClasses", "createVersionPropertiesFML");
            userDev.setExtension("jar");
        }
        project.getArtifacts().add("archives", userDev);

        Zip src = makeTask("packageSrc", Zip.class);
        {
            src.setClassifier("src");
            src.from(delayedFile(CHANGELOG));
            src.from(delayedFile(FML_LICENSE));
            src.from(delayedFile(FML_CREDITS));
            src.from(delayedFile("{FML_DIR}/install"), new CopyInto(null, "!*.gradle"));
            src.from(delayedFile("{FML_DIR}/install"), (new CopyInto(null, "*.gradle")).addExpand("version", delayedString("{MC_VERSION}-{VERSION}")).addExpand("name", "forge"));
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
            branch = runGit(project, "rev-parse", "--abbrev-ref", "HEAD");
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
        
        IDelayedResolver resolver = (IDelayedResolver)project.getPlugins().findPlugin("forgedev");
        StringBuilder out = new StringBuilder();

        out.append(DelayedBase.resolve("{MC_VERSION}", project, resolver)).append('-'); // Somehow configure this?
        out.append(major).append('.').append(minor).append('.').append(revision).append('.').append(build);
        if (branch != null)
        {
            out.append('-').append(branch);
        }

        return out.toString();
    }
}

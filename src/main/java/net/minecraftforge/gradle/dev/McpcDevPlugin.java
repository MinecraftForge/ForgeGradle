package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.dev.DevConstants.*;
import net.minecraftforge.gradle.CopyInto;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

public class McpcDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set fmlDir
        getExtension().setFmlDir("forge/fml");
        getExtension().setForgeDir("forge");
        getExtension().setBukkitDir("bukkit");
        
        createJarProcessTasks();
//        createProjectTasks();
//        createEclipseTasks();
//        createMiscTasks();
        createSourceCopyTasks();
//        createPackageTasks();

        // the master setup task.
        Task task = makeTask("setupMcpc", DefaultTask.class);
        task.dependsOn("extractForgeSources", "generateProjects", "eclipse", "copyAssets");
        task.setGroup("Forge");

//        // the master task.
//        task = makeTask("buildPackages");
//        task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "packageUserDev", "packageSrc", "genJavadocs");
//        task.setGroup("Forge");
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
            task4.setDoesCache(false);
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

            // see ZIP_INJECT_FORGE
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

        task4 = makeTask("forgePatchJar", PatchJarTask.class);
        {
            task4.setInJar(delayedFile(ZIP_RENAMED_MCPC));
            task4.setOutJar(delayedFile(ZIP_FORGED_MCPC));
            task4.setInPatches(delayedFile(FORGE_PATCH_DIR));
            task4.setDoesCache(true);
            task4.setMaxFuzz(2);
            task4.dependsOn("remapSourcesJar");
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
            task.from(delayedFile(ZIP_RENAMED_MCPC));
            task.into(delayedFile(ECLIPSE_CLEAN_RES));
            task.dependsOn("extractWorkspace", "remapSourcesJar");
        }

        task = makeTask("extractCleanSource", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.setIncludeEmptyDirs(false);
            task.from(delayedFile(ZIP_RENAMED_MCPC));
            task.into(delayedFile(ECLIPSE_CLEAN_SRC));
            task.dependsOn("extractCleanResources");
        }

        task = makeTask("extractMcpcResources", ExtractTask.class);
        {
            task.exclude(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_MCPC));
            task.into(delayedFile(ECLIPSE_FORGE_RES));
            task.dependsOn("forgePatchJar", "extractWorkspace");
        }

        task = makeTask("extractMcpcSources", ExtractTask.class);
        {
            task.include(JAVA_FILES);
            task.from(delayedFile(ZIP_PATCHED_MCPC));
            task.into(delayedFile(ECLIPSE_FORGE_SRC));
            task.dependsOn("extractMcpcResources");
        }
    }
}

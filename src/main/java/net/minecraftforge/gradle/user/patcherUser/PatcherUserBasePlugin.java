package net.minecraftforge.gradle.user.patcherUser;

import static net.minecraftforge.gradle.common.Constants.DIR_JSONS;
import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.common.Constants.TASK_DL_VERSION_JSON;
import static net.minecraftforge.gradle.common.Constants.TASK_GENERATE_SRGS;
import static net.minecraftforge.gradle.user.UserConstants.*;
import static net.minecraftforge.gradle.user.patcherUser.PatcherUserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.ExtractConfigTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.RemapSources;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.delayed.TokenReplacer;

import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public abstract class PatcherUserBasePlugin<T extends PatcherUserBaseExtension> extends UserBasePlugin<T>
{
    @Override
    @SuppressWarnings("serial")
    protected void applyUserPlugin()
    {
        // add the MC setup tasks..
        String global = DIR_API_JAR_BASE + "/" + REPLACE_API_NAME + "%s-" + REPLACE_API_VERSION;
        String local = DIR_LOCAL_CACHE + "/" + REPLACE_API_NAME + "%s-" + REPLACE_API_VERSION + "-PROJECT(" + project.getName() + ")";
        this.tasksMerged(global, local);

        // setup userdev
        {
            project.getConfigurations().maybeCreate(CONFIG_USERDEV);

            ExtractConfigTask extractUserdev = makeTask(TASK_EXTRACT_USERDEV, ExtractConfigTask.class);
            extractUserdev.setDestinationDir(delayedFile(DIR_USERDEV));
            extractUserdev.setConfig(CONFIG_USERDEV);
            extractUserdev.exclude("META-INF/**", "META-INF/**");
            extractUserdev.dependsOn(TASK_DL_VERSION_JSON);

            extractUserdev.doLast(new Closure<Boolean>(project) // normalizes to linux endings
            {
                @Override
                public Boolean call()
                {
                    parseAndStoreVersion(delayedFile(JSON_USERDEV).call(), delayedFile(DIR_JSONS).call());
                    return true;
                }
            });

            // See afterEvaluate for more config

            project.getTasks().getByName(TASK_GENERATE_SRGS).dependsOn(extractUserdev);
            project.getTasks().getByName(TASK_RECOMPILE).dependsOn(extractUserdev);
            project.getTasks().getByName(TASK_MAKE_START).dependsOn(extractUserdev);
        }

        // setup patching

        // add the binPatching task
        {
            final Object patchedJar = chooseDeobfOutput(global, local, "", "binpatched");

            TaskApplyBinPatches task = makeTask("applyBinPatches", TaskApplyBinPatches.class);
            task.setInJar(delayedFile(JAR_MERGED));
            task.setOutJar(patchedJar);
            task.setPatches(delayedFile(BINPATCH_USERDEV));
            task.setClassJar(delayedFile(JAR_UD_CLASSES));
            task.setResourceJar(delayedTree(ZIP_UD_RES));
            task.dependsOn("mergeJars");

            project.getTasks().getByName(TASK_DEOBF_BIN).dependsOn(task);

            DeobfuscateJar deobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN).dependsOn(task);

            deobf.setInJar(patchedJar);
            deobf.dependsOn(task);
        }

        // add source patching task
        {
            final Object decompFixed = chooseDeobfOutput(global, local, "", "decompFixed");
            final Object patched = chooseDeobfOutput(global, local, "", "patched");

            ProcessSrcJarTask patch = makeTask("processSources", ProcessSrcJarTask.class);
            patch.dependsOn("decompile");
            patch.setInJar(decompFixed);
            patch.setOutJar(patched);

            RemapSources remap = (RemapSources) project.getTasks().getByName(TASK_REMAP);
            remap.setInJar(patched);
            remap.dependsOn(patch);
        }

    }

    @Override
    protected void afterEvaluate()
    {
        // add replacements
        T ext = getExtension();
        TokenReplacer.putReplacement(REPLACE_API_GROUP, getApiGroup(ext));
        TokenReplacer.putReplacement(REPLACE_API_NAME, getApiName(ext));
        TokenReplacer.putReplacement(REPLACE_API_VERSION, getApiVersion(ext));

        // add userdev dep
        project.getDependencies().add(CONFIG_USERDEV, delayedString(""
                + REPLACE_API_GROUP + ":"
                + REPLACE_API_NAME + ":"
                + REPLACE_API_VERSION + ":"
                + getUserdevClassifier(ext) + "@"
                + getUserdevExtension(ext)
                ));
    }

    @Override
    protected void addAtsToDeobf()
    {
        // add src ATs
        DeobfuscateJar binDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF_BIN);
        DeobfuscateJar decompDeobf = (DeobfuscateJar) project.getTasks().getByName(TASK_DEOBF);

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addTransformer(extAts);
        decompDeobf.addTransformer(extAts);

        // grab ATs from resource dirs
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        SourceSet main = javaConv.getSourceSets().getByName("main");
        SourceSet api = javaConv.getSourceSets().getByName("api");

        boolean addedAts = false;

        for (File at : main.getResources().getFiles())
        {
            if (at.getName().toLowerCase().endsWith("_at.cfg"))
            {
                project.getLogger().lifecycle("Found AccessTransformer in main resources: " + at.getName());
                binDeobf.addTransformer(at);
                decompDeobf.addTransformer(at);
                addedAts = true;
            }
        }

        for (File at : api.getResources().getFiles())
        {
            if (at.getName().toLowerCase().endsWith("_at.cfg"))
            {
                project.getLogger().lifecycle("Found AccessTransformer in api resources: " + at.getName());
                binDeobf.addTransformer(at);
                decompDeobf.addTransformer(at);
                addedAts = true;
            }
        }

        // TODO: search dependency jars for resources

        useLocalCache = useLocalCache || addedAts;
    }

    @Override
    protected void afterDecomp(boolean isDecomp, boolean useLocalCache, String mcConfig)
    {
        // TODO: add MC dep
    }

    public abstract String getApiGroup(T ext);

    public abstract String getApiName(T ext);

    public abstract String getApiVersion(T ext);

    public abstract String getUserdevClassifier(T ext);

    public abstract String getUserdevExtension(T ext);

    //@formatter:off
    @Override protected boolean hasServerRun() { return true; }
    @Override protected boolean hasClientRun() { return true; }
    @Override protected void applyOverlayPlugin() { }
    @Override public boolean canOverlayPlugin() { return false; }
    @Override protected T getOverlayExtension() { return null; }
    //@formatter:on
}

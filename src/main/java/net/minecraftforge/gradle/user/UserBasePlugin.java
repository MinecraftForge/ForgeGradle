package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJar;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.util.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;

public abstract class UserBasePlugin<T extends UserExtension> extends BasePlugin<T>
{
    @Override
    public final void applyPlugin()
    {
        // apply the plugins
        this.applyExternalPlugin("java");
        this.applyExternalPlugin("maven");
        this.applyExternalPlugin("eclipse");
        this.applyExternalPlugin("idea");

        // life cycle tasks
        Task task = makeTask(TASK_SETUP_CI, DefaultTask.class);
        task.setDescription("Sets up the bare minimum to build a minecraft mod. Ideally for CI servers");
        task.setGroup("ForgeGradle");

        task = makeTask(TASK_SETUP_DEV, DefaultTask.class);
        task.setDescription("CIWorkspace + natives and assets to run and test Minecraft");
        task.setGroup("ForgeGradle");

        task = makeTask(TASK_SETUP_DECOMP, DefaultTask.class);
        task.setDescription("DevWorkspace + the deobfuscated Minecraft source linked as a source jar.");
        task.setGroup("ForgeGradle");

        applyUserPlugin();
    }

    protected abstract void applyUserPlugin();

    protected void tasksClient(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_CLIENT_FRESH), delayedFile(MCP_PATCHES_CLIENT));
    }

    protected void tasksServer(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_SERVER_FRESH), delayedFile(MCP_PATCHES_MERGED));
    }

    protected void tasksMerged(String globalPattern, String localPattern, String taskSuffix)
    {
        makeDecompTasks(globalPattern, localPattern, taskSuffix, delayedFile(JAR_MERGED), delayedFile(MCP_PATCHES_MERGED));
    }

    private void makeDecompTasks(String globalOutputPattern, String localOutputPattern, String taskSuffix, Object inputJar, Object mcpPatchSet)
    {
        DeobfuscateJar deobfBin = makeTask(TASK_DEOBF_BIN + taskSuffix, DeobfuscateJar.class);
        {
            deobfBin.setSrg(delayedFile(SRG_NOTCH_TO_MCP));
            deobfBin.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfBin.setExceptorCfg(delayedFile(EXC_MCP));
            deobfBin.setApplyMarkers(false);
            deobfBin.setInJar(inputJar);
            deobfBin.setOutJar(chooseDeobfOutput(globalOutputPattern, localOutputPattern, "Bin"));
            deobfBin.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }

        Object deobfDecompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-deobfDecomp");
        Object decompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-decomp");
        Object postDecompJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "-sources");
        Object recompiledJar = chooseDeobfOutput(globalOutputPattern, localOutputPattern, "");

        DeobfuscateJar deobfDecomp = makeTask(TASK_DEOBF + taskSuffix, DeobfuscateJar.class);
        {
            deobfDecomp.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfDecomp.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfDecomp.setExceptorCfg(delayedFile(EXC_SRG));
            deobfDecomp.setApplyMarkers(false);
            deobfDecomp.setInJar(inputJar);
            deobfBin.setOutJar(deobfDecompJar);
            deobfDecomp.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }

        ApplyFernFlowerTask decompile = makeTask(TASK_DECOMPILE + taskSuffix, ApplyFernFlowerTask.class);
        {
            decompile.setInJar(deobfDecompJar);
            decompile.setOutJar(decompJar);
            decompile.setFernflower(delayedFile(JAR_FERNFLOWER));
            decompile.dependsOn(TASK_DL_FERNFLOWER, deobfDecomp);
        }

        PostDecompileTask postDecomp = makeTask(TASK_POST_DECOMP + taskSuffix, PostDecompileTask.class);
        {
            postDecomp.setInJar(decompJar);
            postDecomp.setOutJar(postDecompJar);
            postDecomp.setPatches(delayedFile(MCP_PATCHES_MERGED));
            postDecomp.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecomp.dependsOn(decompile);
        }

        TaskRecompileMc recompile = makeTask(TASK_RECOMPILE + taskSuffix, TaskRecompileMc.class);
        {
            recompile.setInSources(postDecompJar);
            recompile.setClasspath(CONFIG_MC_DEPS);
            recompile.setOutJar(recompiledJar);
            recompile.dependsOn(postDecomp);
        }

        // add setup dependencies
        project.getTasks().getByName(TASK_SETUP_CI).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DEV).dependsOn(deobfBin);
        project.getTasks().getByName(TASK_SETUP_DECOMP).dependsOn(recompile);
    }

    /**
     * This method returns a closure that
     * @return
     */
    @SuppressWarnings("serial")
    private Object chooseDeobfOutput(final String globalPattern, final String localPattern, final String classifier)
    {
        return new Closure<DelayedFile>(project, this) {
            public DelayedFile call()
            {
                String str = useLocalCache() ? localPattern : globalPattern;
                return delayedFile(String.format(str, classifier));
            }
        };
    }

    /**
     * This method is called sufficiently late. Either afterEvaluate or inside a task.
     * This method is called to decide whether or not to use the project-local cache instead of the global cache.
     * The actual locations of each cache are specified elsewhere. // TODO AD SEE THING
     * @return
     */
    protected abstract boolean useLocalCache();
}

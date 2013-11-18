package net.minecraftforge.gradle.user;

import java.io.File;

import org.gradle.api.Action;
import org.gradle.listener.ActionBroadcast;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.Library;
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;
import static net.minecraftforge.gradle.user.UserConstants.*;

public class ForgeUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ApplyBinPatchesTask binTask = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
        {
            binTask.setInJar(delayedFile(Constants.JAR_MERGED));
            binTask.setOutJar(delayedFile(UserConstants.FORGE_BINPATCHED));
            binTask.setPatches(delayedFile(UserConstants.BINPATCHES));
            binTask.setClassesJar(delayedFile(UserConstants.BINARIES_JAR));
            binTask.setResources(delayedFileTree(UserConstants.RES_DIR));
            binTask.dependsOn("mergeJars");
        }

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.dependsOn(binTask);
            procTask.setInJar(delayedFile(UserConstants.FORGE_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(UserConstants.FORGE_DEOBF_MCP));
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void afterEvaluate()
    {
        String depBase = "net.minecraftforge:forge:" + getExtension().getApiVersion();
        project.getDependencies().add(CONFIG_USERDEV,      depBase + ":userdev");
        project.getDependencies().add(CONFIG_API_JAVADOCS, depBase + ":javadoc@zip");

        super.afterEvaluate();

        final File deobf = delayedFile(FORGE_DEOBF_MCP).call();

        project.getDependencies().add(CONFIG, project.files(deobf));

        EclipseModel eclipseConv = (EclipseModel) project.getExtensions().getByName("eclipse");
        ((ActionBroadcast<Classpath>)eclipseConv.getClasspath().getFile().getWhenMerged()).add(new Action<Classpath>()
        {
            FileReferenceFactory factory = new FileReferenceFactory();
            @Override
            public void execute(Classpath classpath)
            {
                for (ClasspathEntry e : classpath.getEntries())
                {
                    if (e instanceof Library)
                    {
                        Library lib = (Library)e;
                        if (lib.getLibrary().getFile().equals(deobf))
                        {
                            lib.setJavadocPath(factory.fromFile(project.getConfigurations().getByName(UserConstants.CONFIG_API_JAVADOCS).getSingleFile()));
                            //TODO: Add the source attachment here....
                        }
                    }
                }
            }
        });

        fixEclipseProject(ECLIPSE_LOCATION);
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformer(delayedFile(UserConstants.FML_AT));
        task.addTransformer(delayedFile(UserConstants.FORGE_AT));
    }
}

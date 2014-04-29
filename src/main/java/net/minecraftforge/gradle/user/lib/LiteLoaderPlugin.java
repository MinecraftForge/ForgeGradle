package net.minecraftforge.gradle.user.lib;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.LiteLoaderJson;
import net.minecraftforge.gradle.json.LiteLoaderJson.Artifact;
import net.minecraftforge.gradle.json.LiteLoaderJson.VersionObject;
import net.minecraftforge.gradle.tasks.user.EtagDownloadTask;

import org.gradle.api.Action;
import org.gradle.api.Project;

import com.google.common.base.Throwables;

public class LiteLoaderPlugin extends UserLibBasePlugin
{
    private Artifact llArtifact;

    @Override
    public void applyPlugin()
    {
        super.applyPlugin();
        commonApply();
    }

    @Override
    public void applyOverlayPlugin()
    {
        super.applyOverlayPlugin();
        commonApply();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void commonApply()
    {
        // add repo
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addMavenRepo(proj, "liteloaderRepo", "http://dl.liteloader.com/versions/");
            }
        });
        
        final DelayedFile json = delayedFile("{CACHE_DIR}/minecraft/liteloader.json");

        {

            EtagDownloadTask task = makeTask("getLiteLoaderJson", EtagDownloadTask.class);
            task.setUrl("http://dl.liteloader.com/versions/versions.json");
            task.setFile(json);
            task.setDieWithError(false);
            
            // make sure it happens sometime during the build.
            project.getTasks().getByName("setupCIWorkspace").dependsOn(task);
            project.getTasks().getByName("setupDevWorkspace").dependsOn(task);
            project.getTasks().getByName("setupDecompWorkspace").dependsOn(task);
            
            task.doLast(new Action() {

                @Override
                public void execute(Object arg0)
                {
                    EtagDownloadTask task = (EtagDownloadTask) arg0;
                    try
                    {
                        readJsonDep(task.getFile());
                    }
                    catch (IOException e)
                    {
                        Throwables.propagate(e);
                    }
                }

            });
        }

        project.afterEvaluate(new Action() {

            @Override
            public void execute(Object arg0)
            {
                if (json.call().exists())
                {
                    try
                    {
                        readJsonDep(json.call());
                    }
                    catch (IOException e)
                    {
                        Throwables.propagate(e);
                    }
                }
            }

        });
    }

    private final void readJsonDep(File json) throws IOException
    {
        if (llArtifact != null)
        {
            // its already set.. why parse again?
            return;
        }

        String mcVersion = delayedString("{MC_VERSION}").call();

        LiteLoaderJson loaded = JsonFactory.loadLiteLoaderJson(json);
        VersionObject obj = loaded.versions.get(mcVersion);
        if (obj == null)//|| !obj.latest.hasMcp())
            throw new RuntimeException("LiteLoader does not have an ForgeGradle compatible edition for Minecraft " + mcVersion);

        llArtifact = obj.latest;

        // add the dependency.
        project.getLogger().info("LiteLoader dep: "+llArtifact.getMcpDepString());
        project.getDependencies().add(actualApiName(), llArtifact.getMcpDepString());
    }

    @Override
    protected String getClientRunClass()
    {
        return "com.mumfrey.liteloader.debug.Start";
    }

    @Override
    protected Iterable<String> getClientRunArgs()
    {
        return new ArrayList<String>(0);
    }

    @Override
    protected String getServerRunClass()
    {
        return "net.minecraft.server.MinecraftServer";
    }

    @Override
    protected Iterable<String> getServerRunArgs()
    {
        return new ArrayList<String>(0);
    }

    @Override
    String actualApiName()
    {
        return "liteloader";
    }

    @Override
    protected String getJarExtension()
    {
        return "litemod";
    }

    @Override
    public boolean shouldOverrideRunConfigs()
    {
        return true;
    }

}

package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.util.Map.Entry;

import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.AssetIndex.AssetEntry;
import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

public class CopyAssetsTask extends DefaultTask
{
    @Input
    Closure<AssetIndex> assetIndex;

    DelayedFile   assetsDir;

    @OutputDirectory
    DelayedFile   outputDir;

    @TaskAction
    public void doTask()
    {
        try
        {
            AssetIndex index = getAssetIndex();
            File assetsDir = new File(getAssetsDir(), "objects");
            File outputDir = getOutputDir();

            if (!index.virtual)
                return; // shrug

            for (Entry<String, AssetEntry> e : index.objects.entrySet())
            {
                File in = getHashedPath(assetsDir, e.getValue().hash);
                File out = new File(outputDir, e.getKey());

                // check existing
                if (out.exists() && out.length() == e.getValue().size)
                    continue;
                else
                {
                    out.getParentFile().mkdirs();
                    Files.copy(in, out);
                }
            }
        }
        catch (Throwable t)
        {
            // CRASH!
            getLogger().error("Something went wrong with the assets copying");
            this.setDidWork(false);
            return;
        }
    }

    private File getHashedPath(File base, String hash)
    {
        return new File(base, hash.substring(0, 2) + "/" + hash);
    }

    public AssetIndex getAssetIndex()
    {
        return (AssetIndex) assetIndex.call();
    }

    public void setAssetIndex(Closure<AssetIndex> assetIndex)
    {
        this.assetIndex = assetIndex;
    }

    public File getAssetsDir()
    {
        return assetsDir.call();
    }

    public void setAssetsDir(DelayedFile assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    public File getOutputDir()
    {
        return outputDir.call();
    }

    public void setOutputDir(DelayedFile outputDir)
    {
        this.outputDir = outputDir;
    }

}

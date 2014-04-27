package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.ParserConfigurationException;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.AssetIndex.AssetEntry;
import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class DownloadAssetsTask extends DefaultTask
{
    DelayedFile                                assetsDir;

    @Input
    Closure<AssetIndex>                        index;

    private boolean                            errored      = false;
    private final ConcurrentLinkedQueue<Asset> filesLeft    = new ConcurrentLinkedQueue<Asset>();
    private final ArrayList<AssetsThread>      threads      = new ArrayList<AssetsThread>();
    private final File                         minecraftDir = new File(Constants.getMinecraftDirectory(), "assets/objects");

    private static final int                   MAX_THREADS  = Runtime.getRuntime().availableProcessors();
    private static final int                   MAX_TRIES    = 5;

    @TaskAction
    public void doTask() throws ParserConfigurationException, SAXException, IOException, InterruptedException
    {
        File out = new File(getAssetsDir(), "objects");
        out.mkdirs();

        AssetIndex index = getIndex();

        for (Entry<String, AssetEntry> e : index.objects.entrySet())
        {
            Asset asset = new Asset(e.getValue().hash, e.getValue().size);
            File file = new File(out, asset.path);

            // exists but not the right size?? delete
            if (file.exists() && file.length() != asset.size)
                file.delete();

            // does the file exist (still) ??
            if (!file.exists())
                filesLeft.offer(asset);
        }

        getLogger().info("Finished parsing JSON");
        int max = filesLeft.size();
        getLogger().info("Files Missing: " + max + "/" + index.objects.size());

        // get number of threads
        int threadNum = max / 100;
        if (threadNum == 0 && max > 0)
            threadNum++; // atleats 1 thread

        // spawn threads
        for (int i = 0; i < threadNum; i++)
            spawnThread();

        getLogger().info("Threads initially spawned: " + threadNum);

        while (stillRunning())
        {
            int done = max - filesLeft.size();
            getLogger().lifecycle("Current status: " + done + "/" + max + "   " + (int) ((double) done / max * 100) + "%");
            spawnThread();
            Thread.sleep(1000);
        }
        
        if (errored)
        {
            // CRASH!
            getLogger().error("Something went wrong with the assets downloading!");
            this.setDidWork(false);
            return;
        }
    }

    private void spawnThread()
    {
        if (threads.size() < MAX_THREADS)
        {
            getLogger().debug("Spawning thread #" + (threads.size() + 1));
            AssetsThread thread = new AssetsThread();
            thread.start();
            threads.add(thread);
        }
    }

    private boolean stillRunning()
    {
        for (Thread t : threads)
        {
            if (t.isAlive())
            {
                return true;
            }
        }
        getLogger().info("All " + threads.size() + " threads Complete");
        return false;
    }

    public File getAssetsDir()
    {
        return assetsDir.call();
    }

    public void setAssetsDir(DelayedFile assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    public AssetIndex getIndex()
    {
        return index.call();
    }

    public void setIndex(Closure<AssetIndex> index)
    {
        this.index = index;
    }

    private static class Asset
    {
        public final String path;
        public final String hash;
        public final long   size;

        Asset(String hash, long size)
        {
            this.path = hash.substring(0, 2) + "/" + hash;
            this.hash = hash.toLowerCase();
            this.size = size;
        }
    }

    private class AssetsThread extends Thread
    {
        public AssetsThread()
        {
            this.setDaemon(true);
        }

        @Override
        public void run()
        {
            Asset asset;
            while ((asset = filesLeft.poll()) != null)
            {
                for (int i = 1; i < MAX_TRIES + 1; i++)
                {
                    try
                    {
                        File file = new File(getAssetsDir(), "objects/" + asset.path);

                        // does exist? create
                        if (!file.exists())
                        {
                            file.getParentFile().mkdirs();
                            file.createNewFile();
                        }

                        File localMc = new File(minecraftDir, asset.path);
                        BufferedInputStream stream;

                        // check for local copy
                        if (localMc.exists() && Constants.hash(localMc, "SHA1").equals(asset.hash))
                            // if so, copy
                            stream = new BufferedInputStream(Files.newInputStreamSupplier(localMc).getInput());
                        else
                            // otherwise download
                            stream = new BufferedInputStream(new URL(Constants.ASSETS_URL + "/" + asset.path).openStream());

                        Files.write(ByteStreams.toByteArray(stream), file);
                        stream.close();

                        // check hash...
                        String hash = Constants.hash(file, "SHA1");
                        if (asset.hash.equals(hash))
                            break; // hashes are fine;
                        else
                        {
                            file.delete();
                            getLogger().error("download attempt " + i + " failed! : " + asset.hash + " != " + hash);
                        }
                    }
                    catch (Exception e)
                    {
                        getLogger().error("Error downloading asset: " + asset.path);
                        e.printStackTrace();
                        if (!errored)
                            errored = true;
                    }
                }
            }
        }
    }
}

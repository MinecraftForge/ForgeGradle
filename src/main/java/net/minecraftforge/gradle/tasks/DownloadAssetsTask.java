package net.minecraftforge.gradle.tasks;

import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.xml.parsers.ParserConfigurationException;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.xml.sax.SAXException;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class DownloadAssetsTask extends DefaultTask
{
    DelayedFile                                       assetsDir;

    private final ConcurrentLinkedQueue<Asset> filesLeft   = new ConcurrentLinkedQueue<Asset>();
    private final ArrayList<AssetsThread>      threads     = new ArrayList<AssetsThread>();

    private static final int                   MAX_THREADS = Runtime.getRuntime().availableProcessors();

    @TaskAction
    public void doTask() throws ParserConfigurationException, SAXException, IOException, InterruptedException
    {
        File out = getAssetsDir();
        out.mkdirs();

        // get resource XML file
        Node root = new XmlParser().parse(new BufferedInputStream((new URL(Constants.ASSETS_URL)).openStream()));

        getLogger().info("Parsing assets XML");

        // construct a list of [file, hash] maps
        for (Object childNode : ((NodeList) root.get("Contents")))
        {
            Node child = (Node) childNode;

            if (((NodeList) child.get("Size")).text().equals("0"))
            {
                continue;
            }

            String key = ((NodeList) child.get("Key")).text();
            String hash = ((NodeList) child.get("ETag")).text().replace('"', ' ').trim();
            filesLeft.offer(new Asset(key, hash));
        }

        getLogger().info("Finished parsing XML");
        getLogger().info("Files found: " + filesLeft.size());

        int threadNum = filesLeft.size() / 100;
        for (int i = 0; i < threadNum; i++)
            spawnThread();

        getLogger().info("Threads initially spawned: " + threadNum);

        while (stillRunning())
        {
            spawnThread();
            Thread.sleep(1000);
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

    private class Asset
    {
        public final String path;
        public final String hash;

        Asset(String path, String hash)
        {
            this.path = path;
            this.hash = hash.toLowerCase();
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
                try
                {
                    boolean download = false;
                    File file = new File(getAssetsDir(), asset.path);

                    if (!file.exists())
                    {
                        download = true;
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                    }
                    else if (!Constants.hash(file).toLowerCase().equals(asset.hash))
                    {
                        download = true;
                        file.delete();
                        file.createNewFile();
                    }

                    if (download)
                    {
                        URL url = new URL(Constants.ASSETS_URL + "/" + asset.path);
                        BufferedInputStream stream = new BufferedInputStream(url.openStream());
                        Files.write(ByteStreams.toByteArray(stream), file);
                        stream.close();
                    }
                }
                catch (Exception e)
                {
                    getLogger().error("Error downloading asset: " + asset.path);
                    e.printStackTrace();
                }
            }
        }
    }
}

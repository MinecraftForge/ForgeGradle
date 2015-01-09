package net.minecraftforge.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;

import com.google.common.collect.ImmutableList;

public class MultiDirSupplier implements InputSupplier, OutputSupplier
{
    private final List<File> dirs;

    public MultiDirSupplier(Iterable<File> dirs)
    {
        this.dirs = ImmutableList.copyOf(dirs);
    }

    @Override
    public void close() throws IOException
    {
        // nothing to do here...
    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        File f = getFileFor(relPath);
        try
        {
            return f == null ? null : new FileOutputStream(f);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public String getRoot(String resource)
    {
        File dir = getDirFor(resource);
        return dir == null ? null : dir.getAbsolutePath();
    }

    @Override
    public InputStream getInput(String relPath)
    {
        File f = getFileFor(relPath);
        try
        {
            return f == null ? null : new FileInputStream(f);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        // stolen from the FolderSupplier.
        LinkedList<String> out = new LinkedList<String>();
        Stack<File> dirStack = new Stack<File>();

        for (File root : dirs)
        {
            dirStack.push(root);
            int rootCut = root.getAbsolutePath().length() + 1; // +1 for the slash

            while (dirStack.size() > 0)
            {
                for (File f : dirStack.pop().listFiles())
                {
                    if (f.isDirectory())
                        dirStack.push(f);
                    else if (f.getPath().endsWith(endFilter))
                        out.add(f.getAbsolutePath().substring(rootCut));
                }
            }
        }

        return out;
    }

    /**
     * returns null if no such file exists in any of the directories.
     */
    private File getFileFor(String rel)
    {
        for (File dir : dirs)
        {
            File file = new File(dir, rel);
            if (file.exists())
                return file;
        }

        return null;
    }

    /**
     * returns null if no such file exists in any of the directories.
     */
    private File getDirFor(String rel)
    {
        for (File dir : dirs)
        {
            File file = new File(dir, rel);
            if (file.exists())
                return dir;
        }

        return null;
    }
}

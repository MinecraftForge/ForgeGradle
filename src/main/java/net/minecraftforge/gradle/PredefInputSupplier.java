package net.minecraftforge.gradle;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.srg2source.util.io.InputSupplier;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class PredefInputSupplier implements InputSupplier
{
    private final Map<String, byte[]> fileMap = Maps.newHashMap();
    private final Map<String, String> rootMap = Maps.newHashMap();

    @Override
    public void close() throws IOException
    {
        // uh.. no?
    }

    @Override
    public String getRoot(String resource)
    {
        return rootMap.get(sanitize(resource));
    }

    @Override
    public InputStream getInput(String relPath)
    {
        return new ByteArrayInputStream(fileMap.get(sanitize(relPath)));
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        LinkedList<String> out = Lists.newLinkedList();
        for (String s : fileMap.keySet())
        {
            if (s.endsWith(endFilter))
            {
                out.add(s);
            }
        }

        return out;
    }

    public void addFile(String path, File root, byte[] data) throws IOException
    {
        path = sanitize(path);
        fileMap.put(path, data);
        rootMap.put(path, sanitize(root.getCanonicalPath()));
    }

    private String sanitize(String in)
    {
        if (in == null)
        {
            return null;
        }
        
        in = in.replace('\\', '/');

        if (in.endsWith("/"))
            in = in.substring(0, in.length() - 1);

        return in;
    }

    public boolean isEmpty()
    {
        return fileMap.isEmpty() && rootMap.isEmpty();
    }
}

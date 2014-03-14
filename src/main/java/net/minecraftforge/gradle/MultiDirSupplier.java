package net.minecraftforge.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;

public class MultiDirSupplier implements InputSupplier, OutputSupplier
{
    public MultiDirSupplier(Iterable<File> dirs)
    {
        // TODO: implement.
    }
    
    @Override
    public void close() throws IOException
    {
        // TODO Auto-generated method stub

    }

    @Override
    public OutputStream getOutput(String relPath)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getRoot(String resource)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public InputStream getInput(String relPath)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        // TODO Auto-generated method stub
        return null;
    }

}

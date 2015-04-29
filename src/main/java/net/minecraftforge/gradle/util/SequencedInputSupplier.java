package net.minecraftforge.gradle.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.srg2source.util.io.InputSupplier;

public class SequencedInputSupplier extends ArrayList<InputSupplier> implements InputSupplier
{
    private static final long serialVersionUID = 1L;

    public SequencedInputSupplier(InputSupplier supp)
    {
        super();
        this.add(supp);
    }
    
    public SequencedInputSupplier()
    {
        super();
    }
    
    public SequencedInputSupplier(int size)
    {
        super(size);
    }

    @Override
    public void close() throws IOException
    {
        for (InputSupplier sup : this)
            sup.close();
    }

    @Override
    public String getRoot(String resource)
    {
        for (InputSupplier sup : this)
        {
            String out =  sup.getRoot(resource);
            if (out != null)
            {
                return out;
            }
        }
        
        return null;
    }

    @Override
    public InputStream getInput(String relPath)
    {
        for (InputSupplier sup : this)
        {
            InputStream out =  sup.getInput(relPath);
            if (out != null)
            {
                return out;
            }
        }
        
        return null;
    }

    @Override
    public List<String> gatherAll(String endFilter)
    {
        LinkedList<String> all = new LinkedList<String>();
        for (InputSupplier sup : this)
            all.addAll(sup.gatherAll(endFilter));
        return all;
    }
}

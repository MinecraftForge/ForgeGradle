/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
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

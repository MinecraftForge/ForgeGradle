package net.minecraftforge.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import net.minecraftforge.srg2source.util.io.InputSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

public class MultiDirSupplierTest
{
    private final List<File> dirs = new LinkedList<File>();
    private final Multimap<File, String> expectedFiles = HashMultimap.create();
    private final Random rand = new Random();
    private static final String END = ".tmp";
    
    @Before // before each test
    public void setup() throws IOException
    {
        int dirNum = rand.nextInt(4) + 1; // 0-5
        
        for (int i = 0; i < dirNum; i++)
        {
            // create and add dir
            File dir = Files.createTempDir().getCanonicalFile();
            dirs.add(dir);
            
            int fileNum = rand.nextInt(9) + 1; // 0-10
            for (int j = 0; j < fileNum; j++)
            {
                File f = File.createTempFile(""+j + "tmp-", END, dir);
                expectedFiles.put(dir, getRelative(dir, f));
            }
        }
    }
    
    @After // after each test
    public void cleanup()
    {
        // delete the files.
        for (File f : dirs)
            delete(f);
        
        // empty the variables.
        dirs.clear();
        expectedFiles.clear();
    }
    
    /**
     * Deletes the specified file or directory.
     */
    private void delete(File f)
    {
        if (f.isFile())
            f.delete();
        else // mst be a dir.
        {
            for (File file : f.listFiles())
                delete(file);
        }
    }
    
    /**
     * Returns the path of the child relative to the provided root. Assumes that the child is actually a child of the provided root.
     */
    private String getRelative(File root, File child) throws IOException
    {
        return child.getCanonicalPath().substring(root.getCanonicalPath().length() + 1); // + 1 for the slash
    }
    
    @Test
    public void testGatherAll() throws IOException
    {
        InputSupplier supp = new MultiDirSupplier(dirs);
        
        // gather all the relative paths
        for (String rel : supp.gatherAll(END))
        {
            Assert.assertTrue(expectedFiles.containsValue(rel));
        }
        
        supp.close(); // to please the compiler..
    }
    
    @Test
    public void testGetRoot() throws IOException
    {
        InputSupplier supp = new MultiDirSupplier(dirs);
        
        for (File dir : expectedFiles.keySet())
        {
            for (String rel : expectedFiles.get(dir))
            {
                Assert.assertEquals(dir, new File(supp.getRoot(rel)).getCanonicalFile());
            }
        }
        
        supp.close(); // to please the compiler..
    }
    
    @Test
    public void testIOStreams() throws IOException
    {
        // to keep track of changes to check later.
        HashMap<String, byte[]> dataMap = new HashMap<String, byte[]>(expectedFiles.size());
        
        // its both an input and output supplier.
        MultiDirSupplier supp = new MultiDirSupplier(dirs);
        
        // write a bunch of random bytes to each file.
        for (String resource : supp.gatherAll(END))
        {
            // generate bytes.
            byte[] bytes = new byte[rand.nextInt(90)+10]; // 10-100 bytes
            rand.nextBytes(bytes); // fill with random stuff
            dataMap.put(resource, bytes); // put into the map.
            
            
            OutputStream stream = supp.getOutput(resource);
            stream.write(bytes);
            stream.close();
        }
        
        // this IO supplier shouldnt need closing.. so we dont care here...
        // otherwise we would close the one supplier, and open another.
        
        // read the files, and ensure they are correct.
        for (String resource : supp.gatherAll(END))
        {
            byte[] expected = dataMap.get(resource);
            byte[] actual = new byte[expected.length];
            
            InputStream stream = supp.getInput(resource);
            stream.read(actual);
            stream.close();
            
            Assert.assertArrayEquals(expected, actual);
        }
        
        supp.close(); // to please the compiler..
    }
}

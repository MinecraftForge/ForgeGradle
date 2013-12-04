package net.minecraftforge.gradle.tasks.user;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class SourceCopyTask extends DefaultTask
{
    @InputFiles
    SourceDirectorySet source;
    
    @Input
    HashMap<String, String> replacements = new HashMap<String, String>();
    
    @OutputDirectory
    DelayedFile output;
    
    @TaskAction
    public void doTask() throws IOException
    {
        getLogger().info("INPUTS >> " + source );
        getLogger().info("OUTPUTS >> " + getOutput() );
        getLogger().info("REPLACE >> " + replacements );
        
        // get output
        File out = getOutput();
        if (out.exists())
            deleteDir(out);
        
        out.mkdirs();
        out = out.getCanonicalFile();
        
        // start traversing tree
        for (DirectoryTree dirTree : source.getSrcDirTrees())
        {
            File dir = dirTree.getDir();
            getLogger().info("PARSING DIR >> " + dir );
         
            // handle nonexistant srcDirs
            if (!dir.exists() || !dir.isDirectory())
                continue;
            else
                dir = dir.getCanonicalFile();
            
            FileTree tree = getProject().fileTree(dir).matching(source.getFilter());
            
            for (File file : tree)
            {
                getLogger().info("PARSING FILE IN >> " + file);
                String text = Files.toString(file, Charsets.UTF_8);
                
                for (Entry<String, String> entry : replacements.entrySet())
                    text = text.replaceAll(entry.getKey(), entry.getValue());
                
                File dest = getDest(file, dir, out);
                getLogger().info("PARSING FILE OUT >> " + dest);
                dest.getParentFile().mkdirs();
                dest.createNewFile();
                Files.write(text, dest, Charsets.UTF_8);
            }
        }
    }
    
    private File getDest(File in, File base, File baseOut) throws IOException
    {
        String relative = in.getCanonicalPath().replace(base.getCanonicalPath(), "");
        return new File(baseOut, relative);
    }
    
    private boolean deleteDir(File dir)
    {
        if (dir.exists())
        {
            File[] files = dir.listFiles();
            if (null != files)
            {
                for (int i = 0; i < files.length; i++)
                {
                    if (files[i].isDirectory())
                    {
                        deleteDir(files[i]);
                    }
                    else
                    {
                        files[i].delete();
                    }
                }
            }
        }
        return (dir.delete());
    }

    public File getOutput()
    {
        return output.call();
    }

    public void setOutput(DelayedFile output)
    {
        this.output = output;
    }
    
    public void setSource(SourceDirectorySet source)
    {
        this.source = source;
    }

    public FileCollection getSource()
    {
        return source;
    }
    
    public void replace(String key, String val)
    {
        replacements.put(key, val);
    }
    
    public void replace(Map<String, String> map)
    {
        for (Entry<String, String> e : map.entrySet())
        {
            replace(Pattern.quote(e.getKey()), e.getValue());
        }
    }
    
    public HashMap<String, String> getReplacements()
    {
        return replacements;
    }
}

package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class TaskExtractDepAts extends DefaultTask
{
    @InputFiles
    private List<FileCollection> collections;
    @OutputDirectory
    private Object               outputDir;

    @TaskAction
    public void doTask() throws IOException
    {
        FileCollection col = getCollections();
        File outputDir = getOutputDir();
        outputDir.mkdirs(); // make sur eit exists
        
        // make a list of things to delete...
        List<File> toDelete = Lists.newArrayList(outputDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File f)
            {
                return f.isFile();
            }
        }));

        Splitter splitter = Splitter.on(' ');

        for (File f : col)
        {
            if (!f.exists() || !f.getName().endsWith("jar"))
                continue;

            JarFile jar = new JarFile(f);
            Manifest man = jar.getManifest();

            if (man != null)
            {
                String atString = man.getMainAttributes().getValue("FMLAT");
                if (!Strings.isNullOrEmpty(atString))
                {
                    for (String at : splitter.split(atString.trim()))
                    {
                        // append _at.cfg just in case its not there already...
                        // also differentiate the file name, in cas the same At comes from multiple jars.. who knows why...
                        File outFile = new File(outputDir, at + "_" + Files.getNameWithoutExtension(f.getName()) + "_at.cfg");
                        toDelete.remove(outFile);
                        
                        JarEntry entry = jar.getJarEntry("META-INF/" + at);

                        InputStream istream = jar.getInputStream(entry);
                        OutputStream ostream = new FileOutputStream(outFile);
                        ByteStreams.copy(istream, ostream);

                        istream.close();
                        ostream.close();
                    }
                }
            }

            jar.close();
        }
        
        // remove the files that shouldnt be there...
        for (File f : toDelete)
        {
            f.delete();
        }
    }

    public FileCollection getCollections()
    {
        return getProject().files(collections);
    }

    public void addCollection(FileCollection col)
    {
        collections.add(col);
    }

    public File getOutputDir()
    {
        return getProject().file(outputDir);
    }

    public void setOutputDir(Object outputFile)
    {
        this.outputDir = outputFile;
    }
}

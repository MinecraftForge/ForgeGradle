package net.minecraftforge.gradle.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.gradle.PredefInputSupplier;
import net.minecraftforge.gradle.SequencedInputSupplier;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.code.regexp.Pattern;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ExtractS2SRangeTask extends DefaultTask
{
    @InputFiles
    private FileCollection libs;
    private DelayedFile projectFile; // to get classpath from a subproject
    private String projectConfig; // Also for a subProject
    private boolean includeJar = false; //Include the 'jar' task for subproject.
    
    // stuff defined on the tasks..
    private final List<DelayedFile> in = new LinkedList<DelayedFile>();
    
    @OutputFile
    private DelayedFile rangeMap;
    
    private boolean allCached = false;
    private static final Pattern FILE_FROM = Pattern.compile("\\s+@\\|([\\w\\d/.]+)\\|.*$");
    private static final Pattern FILE_START = Pattern.compile("\\s*Class Start\\: ([\\w\\d.]+)$");
    
    @TaskAction
    public void doTask() throws IOException
    {
        List<File> ins = getIn();
        File rangemap = getRangeMap();
        
        InputSupplier inSup;
        
        if (ins.size() == 0)
            return; // no input.
        else if (ins.size() == 1)
        {
            // just 1 supplier.
            inSup = getInput(ins.get(0));
        }
        else
        {
            // multinput
            inSup = new SequencedInputSupplier();
            for (File f : ins)
            {
                ((SequencedInputSupplier) inSup).add(getInput(f));
            }
        }
        
        // cache
        inSup = cacheInputs(inSup, rangemap);
        
        if (rangemap.exists())
        {
            if (allCached)
            {
                return;
            }
            
            List<String> files = inSup.gatherAll(".java");
            
            // read rangemap
            List<String> lines = Files.readLines(rangemap, Charsets.UTF_8);
            {
                Iterator<String> it = lines.iterator();
                while(it.hasNext())
                {
                    String line = it.next();
                    
                    com.google.code.regexp.Matcher match;
                    String fileMatch = null;
                    if (line.trim().startsWith("@"))
                    {
                        match = FILE_FROM.matcher(line);
                        if (match.matches())
                        {
                            fileMatch = match.group(1).replace('\\', '/');
                        }
                    }
                    else
                    {
                        match = FILE_START.matcher(line);
                        if (match.matches())
                        {
                            fileMatch = match.group(1).replace('.', '/') + ".java";
                        }
                    }
                    
                    if (fileMatch != null && files.contains(fileMatch))
                    {
                        it.remove();
                    }
                }
            }
            
            generateRangeMap(inSup, rangemap);
            
            lines.addAll(Files.readLines(rangemap, Charsets.UTF_8));
            Files.write(Joiner.on(Constants.NEWLINE).join(lines), rangemap, Charsets.UTF_8);
        }
        else
        {
            generateRangeMap(inSup, rangemap);
        }
    }
    
    private InputSupplier cacheInputs(InputSupplier input, File out) throws IOException
    {
        boolean outExists = out.exists();
        
        // read the cache
        File cacheFile = new File(out + ".inputCache");
        HashSet<CacheEntry> cache = readCache(cacheFile);
        
        // generate the cache
        List<String> strings = input.gatherAll(".java");
        HashSet<CacheEntry> genCache = Sets.newHashSetWithExpectedSize(strings.size());
        PredefInputSupplier predef = new PredefInputSupplier();
        for (String rel : strings)
        {
            File root = new File(input.getRoot(rel)).getCanonicalFile();
            
            InputStream fis = input.getInput(rel);
            byte[] array = ByteStreams.toByteArray(fis);
            fis.close();
            
            CacheEntry entry = new CacheEntry(rel, root, Constants.hash(array));
            genCache.add(entry);
            
            if (!outExists || !cache.contains(entry))
            {
                predef.addFile(rel, root, array);
            }
        }
        
        if (!predef.isEmpty())
        {
            writeCache(cacheFile, genCache);
        }
        else
        {
            allCached = true;
        }

        return predef;
    }
    
    private HashSet<CacheEntry> readCache(File cacheFile) throws IOException
    {
        if (!cacheFile.exists())
            return Sets.newHashSetWithExpectedSize(0);
        
        List<String> lines = Files.readLines(cacheFile, Charsets.UTF_8);
        HashSet<CacheEntry> cache = Sets.newHashSetWithExpectedSize(lines.size());
        
        for (String s : lines)
        {
            String[] tokens = s.split(";");
            if (tokens.length != 3)
            {
                getLogger().info("Corrupted input cache! {}", cacheFile);
                break;
            }
            cache.add(new CacheEntry(tokens[0], new File(tokens[1]), tokens[2]));
        }
        
        return cache;
    }
    
    private void writeCache(File cacheFile, Collection<CacheEntry> cache) throws IOException
    {
        if (cacheFile.exists())
            cacheFile.delete();
        
        cacheFile.getParentFile().mkdirs();
        cacheFile.createNewFile();
        
        BufferedWriter writer = Files.newWriter(cacheFile, Charsets.UTF_8);
        for (CacheEntry e : cache)
        {
            writer.write(e.toString());
            writer.newLine();
        }
        
        writer.close();
    }
    
    private void generateRangeMap(InputSupplier inSup, File rangeMap)
    {
        RangeExtractor extractor = new RangeExtractor();
        extractor.addLibs(getLibs().getAsPath()).setSrc(inSup);
        
        PrintStream stream = new PrintStream(Constants.createLogger(getLogger(), LogLevel.DEBUG));
        extractor.setOutLogger(stream);
        
        boolean worked = extractor.generateRangeMap(rangeMap);
        
        stream.close();
        
        if (!worked)
            throw new RuntimeException("RangeMap generation Failed!!!");
    }
    
    private InputSupplier getInput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            ZipInputSupplier supp = new ZipInputSupplier();
            supp.readZip(f);
            return supp;
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
    }
    
    public File getRangeMap()
    {
        return rangeMap.call();
    }

    public void setRangeMap(DelayedFile out)
    {
        this.rangeMap = out;
    }

    @InputFiles
    public FileCollection getIns()
    {
        return getProject().files(in);
    }
    
    public List<File> getIn()
    {
        List<File> files = new LinkedList<File>();
        for (DelayedFile f : in)
            files.add(f.call());
        return files;
    }

    public void addIn(DelayedFile in)
    {
        this.in.add(in);
    }
    
    public FileCollection getLibs()
    {
        if (projectFile != null && libs == null) // libs == null to avoid doing this any more than necessary..
        {
            File buildscript = projectFile.call();
            if (!buildscript.exists())
                return null;
            
            Project proj = BasePlugin.getProject(buildscript, getProject());
            libs = proj.getConfigurations().getByName(projectConfig);

            if (includeJar)
            {
                AbstractTask jarTask = (AbstractTask)proj.getTasks().getByName("jar");
                executeTask(jarTask);
                File compiled = (File)jarTask.property("archivePath");
                libs = getProject().files(compiled, libs);
            }
        }
        
        return libs;
    }

    private void executeTask(AbstractTask task)
    {
        for (Object dep : task.getTaskDependencies().getDependencies(task))
        {
            executeTask((AbstractTask) dep);
        }

        if (!task.getState().getExecuted())
        {
            getLogger().lifecycle(task.getPath());
            task.execute();
        }
    }

    public void setLibs(FileCollection libs)
    {
        this.libs = libs;
    }
    
    public void setLibsFromProject(DelayedFile buildscript, String config, boolean includeJar)
    {
        this.projectFile = buildscript;
        this.projectConfig = config;
        this.includeJar = includeJar;
    }
    
    private static class CacheEntry
    {
        public final String path, hash;
        public final File root;
        
        public CacheEntry(String path, File root, String hash) throws IOException
        {
            this.path = path.replace('\\', '/');
            this.hash = hash;
            this.root = root.getCanonicalFile();
        }
        
        @Override
        public int hashCode()
        {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((hash == null) ? 0 : hash.hashCode());
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            result = prime * result + ((root == null) ? 0 : root.hashCode());
            return result;
        }
        
        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheEntry other = (CacheEntry) obj;
            if (hash == null)
            {
                if (other.hash != null)
                    return false;
            }
            else if (!hash.equals(other.hash))
                return false;
            if (path == null)
            {
                if (other.path != null)
                    return false;
            }
            else if (!path.equals(other.path))
                return false;
            if (root == null)
            {
                if (other.root != null)
                    return false;
            }
            else if (!root.getAbsolutePath().equals(other.root.getAbsolutePath()))
                return false;
            return true;
        }
        
        @Override
        public String toString()
        {
            return ""+path+";"+root+";"+hash;
        }
    }
}

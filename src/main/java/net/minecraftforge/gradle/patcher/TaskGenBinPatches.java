package net.minecraftforge.gradle.patcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;
import java.util.zip.Adler32;

import lzma.streams.LzmaOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.nothome.delta.Delta;

class TaskGenBinPatches extends DefaultTask
{
    //@formatter:off
    @InputFile  private Object cleanClient;
    @InputFile  private Object cleanServer;
    @InputFile  private Object cleanMerged;
    @InputFile  private Object dirtyJar;
    @InputFile  private Object srg;
    @OutputFile private Object devBinPatches;
    @OutputFile private Object runBinPatches;
    //@formatter:on

    private List<Object>             patchSets    = Lists.newArrayList();
    private HashMap<String, String>  obfMapping   = new HashMap<String, String>();
    private HashMap<String, String>  srgMapping   = new HashMap<String, String>();
    private Multimap<String, String> innerClasses = ArrayListMultimap.create();
    private Set<String>              patchedFiles = new HashSet<String>();
    private Delta                    delta        = new Delta();

    //@formatter:off
    public TaskGenBinPatches() { super(); }
    //@formatter:on

    @TaskAction
    public void doTask() throws Exception
    {
        loadMappings();

        for (Object tree : patchSets)
        {
            for (File patch : getProject().files(tree).getFiles())
            {
                String name = patch.getName().replace(".java.patch", "");
                String obfName = srgMapping.get(name);
                patchedFiles.add(obfName);
                addInnerClasses(name, patchedFiles);
            }
        }

        HashMap<String, byte[]> runtime = new HashMap<String, byte[]>();
        HashMap<String, byte[]> devtime = new HashMap<String, byte[]>();

        File dirtyJar = getDirtyJar();

        createBinPatches(runtime, "client/", getCleanClient(), dirtyJar);
        createBinPatches(runtime, "server/", getCleanServer(), dirtyJar);
        createBinPatches(devtime, "merged/", getCleanMerged(), dirtyJar);

        byte[] runtimedata = createPatchJar(runtime);
        runtimedata = pack200(runtimedata);
        runtimedata = compress(runtimedata);
        Files.write(runtimedata, getRuntimeBinPatches());

        byte[] devtimedata = createPatchJar(devtime);
        devtimedata = pack200(devtimedata);
        devtimedata = compress(devtimedata);
        Files.write(devtimedata, getDevBinPatches());
    }

    private void addInnerClasses(String parent, Set<String> patchList)
    {
        // Recursively add inner classes to the list of patches - this will mean we ship anything affected by "access$" changes
        for (String inner : innerClasses.get(parent))
        {
            patchList.add(srgMapping.get(inner));
            addInnerClasses(inner, patchList);
        }
    }

    private void loadMappings() throws Exception
    {
        Files.readLines(getSrg(), Charset.defaultCharset(), new LineProcessor<String>() {

            Splitter splitter = Splitter.on(CharMatcher.anyOf(": ")).omitEmptyStrings().trimResults();

            @Override
            public boolean processLine(String line) throws IOException
            {
                if (!line.startsWith("CL"))
                {
                    return true;
                }

                String[] parts = Iterables.toArray(splitter.split(line), String.class);
                obfMapping.put(parts[1], parts[2]);
                String srgName = parts[2].substring(parts[2].lastIndexOf('/') + 1);
                srgMapping.put(srgName, parts[1]);
                int innerDollar = srgName.lastIndexOf('$');
                if (innerDollar > 0)
                {
                    String outer = srgName.substring(0, innerDollar);
                    innerClasses.put(outer, srgName);
                }
                return true;
            }

            @Override
            public String getResult()
            {
                return null;
            }
        });
    }

    private void createBinPatches(HashMap<String, byte[]> patches, String root, File base, File target) throws Exception
    {
        JarFile cleanJ = new JarFile(base);
        JarFile dirtyJ = new JarFile(target);

        for (Map.Entry<String, String> entry : obfMapping.entrySet())
        {
            String obf = entry.getKey();
            String srg = entry.getValue();

            if (!patchedFiles.contains(obf)) // Not in the list of patch files.. we didn't edit it.
            {
                continue;
            }

            JarEntry cleanE = cleanJ.getJarEntry(obf + ".class");
            JarEntry dirtyE = dirtyJ.getJarEntry(obf + ".class");

            if (dirtyE == null) //Something odd happened.. a base MC class wasn't in the obfed jar?
            {
                continue;
            }

            byte[] clean = (cleanE != null ? ByteStreams.toByteArray(cleanJ.getInputStream(cleanE)) : new byte[0]);
            byte[] dirty = ByteStreams.toByteArray(dirtyJ.getInputStream(dirtyE));

            byte[] diff = delta.compute(clean, dirty);

            ByteArrayDataOutput out = ByteStreams.newDataOutput(diff.length + 50);
            out.writeUTF(obf);                   // Clean name
            out.writeUTF(obf.replace('/', '.')); // Source Notch name
            out.writeUTF(srg.replace('/', '.')); // Source SRG Name
            out.writeBoolean(cleanE != null);    // Exists in Clean
            if (cleanE != null)
            {
                out.writeInt(adlerHash(clean)); // Hash of Clean file
            }
            out.writeInt(diff.length); // Patch length
            out.write(diff);           // Patch

            patches.put(root + srg.replace('/', '.') + ".binpatch", out.toByteArray());
        }

        cleanJ.close();
        dirtyJ.close();
    }

    private int adlerHash(byte[] input)
    {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return (int) hasher.getValue();
    }

    private byte[] createPatchJar(HashMap<String, byte[]> patches) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JarOutputStream jar = new JarOutputStream(out);
        for (Map.Entry<String, byte[]> entry : patches.entrySet())
        {
            jar.putNextEntry(new JarEntry("binpatch/" + entry.getKey()));
            jar.write(entry.getValue());
        }
        jar.close();
        return out.toByteArray();
    }

    private byte[] pack200(byte[] data) throws Exception
    {
        JarInputStream in = new JarInputStream(new ByteArrayInputStream(data));
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Packer packer = Pack200.newPacker();

        SortedMap<String, String> props = packer.properties();
        props.put(Packer.EFFORT, "9");
        props.put(Packer.KEEP_FILE_ORDER, Packer.TRUE);
        props.put(Packer.UNKNOWN_ATTRIBUTE, Packer.PASS);

        final PrintStream err = new PrintStream(System.err);
        System.setErr(new PrintStream(ByteStreams.nullOutputStream()));
        packer.pack(in, out);
        System.setErr(err);

        in.close();
        out.close();

        return out.toByteArray();
    }

    private byte[] compress(byte[] data) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LzmaOutputStream lzma = new LzmaOutputStream.Builder(out).useEndMarkerMode(true).build();
        lzma.write(data);
        lzma.close();
        return out.toByteArray();
    }

    public File getCleanClient()
    {
        return getProject().file(cleanClient);
    }

    public void setCleanClient(Object cleanClient)
    {
        this.cleanClient = cleanClient;
    }

    public File getCleanServer()
    {
        return getProject().file(cleanServer);
    }

    public void setCleanServer(Object cleanServer)
    {
        this.cleanServer = cleanServer;
    }

    public File getCleanMerged()
    {
        return getProject().file(cleanMerged);
    }

    public void setCleanMerged(Object cleanMerged)
    {
        this.cleanMerged = cleanMerged;
    }

    public File getDirtyJar()
    {
        return getProject().file(dirtyJar);
    }

    public void setDirtyJar(Object dirtyJar)
    {
        this.dirtyJar = dirtyJar;
    }

    @InputFiles
    public FileCollection getPatchSets()
    {
        FileCollection collection = null;

        for (Object o : patchSets)
        {
            FileCollection col;
            if (o instanceof FileCollection)
            {
                col = (FileCollection) o;
            }
            else if (o instanceof File && ((File) o).isDirectory())
            {
                col = getProject().fileTree(o);
            }
            else
            {
                col = getProject().files(o);
            }

            if (collection == null)
                collection = col;
            else
                collection = collection.plus(col);
        }

        return collection;
    }

    public void addPatchSet(Object patchList)
    {
        this.patchSets.add(patchList);
    }

    public File getSrg()
    {
        return getProject().file(srg);
    }

    public void setSrg(Object srg)
    {
        this.srg = srg;
    }

    public File getRuntimeBinPatches()
    {
        return getProject().file(runBinPatches);
    }

    public void setRuntimeBinPatches(Object runBinPatches)
    {
        this.runBinPatches = runBinPatches;
    }

    public File getDevBinPatches()
    {
        return getProject().file(devBinPatches);
    }

    public void setDevBinPatches(Object devBinPatches)
    {
        this.devBinPatches = devBinPatches;
    }
}

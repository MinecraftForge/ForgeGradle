package net.minecraftforge.gradle.tasks.dev;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
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
import java.util.zip.ZipEntry;

import lzma.streams.LzmaOutputStream;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.nothome.delta.Delta;

public class GenBinaryPatches extends DefaultTask
{
    @InputFile
    private DelayedFile             cleanClient;

    @InputFile
    private DelayedFile             cleanServer;

    @InputFile
    private DelayedFile             cleanMerged;

    @InputFile
    private DelayedFile             dirtyJar;

    private List<DelayedFileTree>   patchList    = new ArrayList<DelayedFileTree>();

    @InputFile
    private DelayedFile             deobfDataLzma;

    @InputFile
    private DelayedFile             srg;

    @OutputFile
    private DelayedFile             outJar;

    private HashMap<String, String> obfMapping   = new HashMap<String, String>();
    private HashMap<String, String> srgMapping   = new HashMap<String, String>();
    private ArrayListMultimap<String, String> innerClasses   = ArrayListMultimap.create();
    private Set<String>             patchedFiles = new HashSet<String>();
    private Delta                   delta        = new Delta();

    @TaskAction
    public void doTask() throws Exception
    {
        loadMappings();

        for (DelayedFileTree tree : patchList)
        {
            for (File patch : tree.call().getFiles())
            {
                String name = patch.getName().replace(".java.patch", "");
                String obfName = srgMapping.get(name);
                patchedFiles.add(obfName);
                addInnerClasses(name, patchedFiles);
            }
        }

        HashMap<String, byte[]> runtime = new HashMap<String, byte[]>();
        HashMap<String, byte[]> devtime = new HashMap<String, byte[]>();

        createBinPatches(runtime, "client/", getCleanClient(), getDirtyJar());
        createBinPatches(runtime, "server/", getCleanServer(), getDirtyJar());
        createBinPatches(devtime, "merged/", getCleanMerged(), getDirtyJar());

        byte[] runtimedata = createPatchJar(runtime);
        runtimedata = pack200(runtimedata);
        runtimedata = compress(runtimedata);

        byte[] devtimedata = createPatchJar(devtime);
        devtimedata = pack200(devtimedata);
        devtimedata = compress(devtimedata);

        buildOutput(runtimedata, devtimedata);
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

    private void buildOutput(byte[] runtime, byte[] devtime) throws Exception
    {
        JarOutputStream out = new JarOutputStream(new FileOutputStream(getOutJar()));
        JarFile in = new JarFile(getDirtyJar());

        if (runtime != null)
        {
            out.putNextEntry(new JarEntry("binpatches.pack.lzma"));
            out.write(runtime);
        }

        if (devtime != null)
        {
            out.putNextEntry(new JarEntry("devbinpatches.pack.lzma"));
            out.write(devtime);
        }

        for (JarEntry e : Collections.list(in.entries()))
        {
            if (e.isDirectory())
            {
                //out.putNextEntry(e); //Not quite sure how to filter out directories we dont care about..
            }
            else
            {
                if (!e.getName().endsWith(".class") || // It's not a class, we don't want resources or anything
                obfMapping.containsKey(e.getName().replace(".class", ""))) //It's a base class and as such should be in the binpatches
                {
                    continue;
                }

                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);
                out.write(ByteStreams.toByteArray(in.getInputStream(e)));
            }
        }

        out.close();
        in.close();
    }

    public File getCleanClient()
    {
        return cleanClient.call();
    }

    public void setCleanClient(DelayedFile cleanClient)
    {
        this.cleanClient = cleanClient;
    }

    public File getCleanServer()
    {
        return cleanServer.call();
    }

    public void setCleanServer(DelayedFile cleanServer)
    {
        this.cleanServer = cleanServer;
    }

    public File getCleanMerged()
    {
        return cleanMerged.call();
    }

    public void setCleanMerged(DelayedFile cleanMerged)
    {
        this.cleanMerged = cleanMerged;
    }

    public File getDirtyJar()
    {
        return dirtyJar.call();
    }

    public void setDirtyJar(DelayedFile dirtyJar)
    {
        this.dirtyJar = dirtyJar;
    }

    public void addPatchList(DelayedFileTree patchList)
    {
        this.patchList.add(patchList);
    }

    public File getDeobfDataLzma()
    {
        return deobfDataLzma.call();
    }

    public void setDeobfDataLzma(DelayedFile deobfDataLzma)
    {
        this.deobfDataLzma = deobfDataLzma;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }
}

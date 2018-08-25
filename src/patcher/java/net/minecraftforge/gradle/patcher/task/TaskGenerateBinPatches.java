package net.minecraftforge.gradle.patcher.task;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import com.nothome.delta.Delta;

import joptsimple.internal.Strings;
import lzma.streams.LzmaOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TaskGenerateBinPatches extends DefaultTask {
    private static final byte[] EMPTY_DATA = new byte[0];
    private static final Delta DELTA = new Delta();

    private File cleanJar;
    private File dirtyJar;
    private File srg;
    private Set<File> patchSets = new HashSet<>();
    private String side;
    private File output = null;

    @TaskAction
    public void apply() throws IOException {
        BiMap<String, String> classes = HashBiMap.create();
        List<String> lines = com.google.common.io.Files.readLines(getSrg(), StandardCharsets.UTF_8).stream().map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList());
        lines.stream()
        .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
        .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
        .filter(pts -> pts.length == 2 && !pts[0].endsWith("/"))
        .forEach(pts -> classes.put(pts[0], pts[1]));

        Set<String> patches = new TreeSet<>();
        for (File root : getPatchSets()) {
            int base = root.getAbsolutePath().length();
            int suffix = ".java.patch".length();
            Files.walk(root.toPath()).filter(Files::isRegularFile).map(p -> p.toAbsolutePath().toString()).filter(p -> p.endsWith(".java.patch")).forEach(path -> {
                String relative = path.substring(base+1).replace('\\', '/');
                patches.add(relative.substring(0, relative.length() - suffix));
            });
        }

        Map<String, byte[]> binpatches = new TreeMap<>();
        try (ZipFile zclean = new ZipFile(getCleanJar());
             ZipFile zdirty = new ZipFile(getDirtyJar())){

            Map<String, Set<String>> entries = new HashMap<>();
            Collections.list(zclean.entries()).stream().map(e -> e.getName()).filter(e -> e.endsWith(".class")).map(e -> e.substring(0, e.length() - 6)).forEach(e -> {
                int idx = e.indexOf('$');
                if (idx != -1) {
                    entries.computeIfAbsent(e.substring(0, idx), k -> Sets.newHashSet(k)).add(e);
                } else {
                    entries.computeIfAbsent(e, k -> new HashSet<>()).add(e);
                }
            });
            Collections.list(zdirty.entries()).stream().map(e -> e.getName()).filter(e -> e.endsWith(".class")).map(e -> e.substring(0, e.length() - 6)).forEach(e -> {
                int idx = e.indexOf('$');
                if (idx != -1) {
                    entries.computeIfAbsent(e.substring(0, idx), k -> Sets.newHashSet(k)).add(e);
                } else {
                    entries.computeIfAbsent(e, k -> new HashSet<>()).add(e);
                }
            });

            for (String path : patches) {
                String obf = classes.getOrDefault(path, path);
                if (entries.containsKey(obf)) {
                    for (String cls : entries.get(obf)) {
                        String srg = classes.inverse().get(obf);
                        byte[] patch = getBinaryPatch(obf, srg, getData(zclean, cls), getData(zdirty, cls));
                        binpatches.put(srg.replace('/', '.') + ".binpatch", patch);
                    }
                } else {
                    getProject().getLogger().lifecycle("Failed: no source for patch? " + path + " " + obf);
                }
            }
        }

        byte[] data = createJar(binpatches);
        try (FileOutputStream fos = new FileOutputStream(getProject().file("build/" + getName() + "/" + getSide() + ".jar"))) {
            IOUtils.write(data, fos);
        }
        /*
        data = pack200(data);
        try (FileOutputStream fos = new FileOutputStream(getProject().file("build/" + getName() + "/" + getSide() + ".pack"))) {
            IOUtils.write(data, fos);
        }
        */
        data = lzma(data);
        try (FileOutputStream fos = new FileOutputStream(getOutput())) {
            IOUtils.write(data, fos);
        }
    }

    private byte[] getData(ZipFile zip, String cls) throws IOException {
        ZipEntry entry = zip.getEntry(cls + ".class");
        return entry == null ? EMPTY_DATA : IOUtils.toByteArray(zip.getInputStream(entry));
    }
    private int adlerHash(byte[] input) {
        Adler32 hasher = new Adler32();
        hasher.update(input);
        return (int)hasher.getValue();
    }
    private byte[] createJar(Map<String, byte[]> patches) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarOutputStream zout = new JarOutputStream(out)) {
            for (Entry<String, byte[]> e : patches.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setTime(0);
                zout.putNextEntry(entry);
                zout.write(e.getValue());
                zout.closeEntry();
            }
        }
        return out.toByteArray();
    }

    // pack200 is deprecated in J11 so we should not use it.
    // Also, it is designed to compress classfiles in a lossy way... so it's not useful for binpatches....
    /*
    private byte[] pack200(byte[] data) throws IOException
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JarInputStream in = new JarInputStream(new ByteArrayInputStream(data))) {

            Packer packer = Pack200.newPacker();

            Map<String, String> props = packer.properties();
            props.put(Packer.EFFORT, "9");
            props.put(Packer.KEEP_FILE_ORDER, Packer.TRUE);
            props.put(Packer.UNKNOWN_ATTRIBUTE, Packer.PASS);

            final PrintStream err = new PrintStream(System.err);
            try (OutputStream log = new FileOutputStream(getProject().file("build/" + getName() + "/pack.log"))) {
                System.setErr(new PrintStream(log));
                packer.pack(in, out);
            }
            System.setErr(err);
        }

        return out.toByteArray();
    }
    */
    private byte[] lzma(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (LzmaOutputStream lzma = new LzmaOutputStream.Builder(out).useEndMarkerMode(true).build()) {
            lzma.write(data);
        }
        return out.toByteArray();
    }
    private byte[] getBinaryPatch(String obf, String srg, byte[] clean, byte[] dirty) throws IOException {
        byte[] diff = dirty.length == 0 ? EMPTY_DATA : DELTA.compute(clean, dirty);
        ByteArrayDataOutput out = ByteStreams.newDataOutput(diff.length + obf.length() + srg.length() + 1);
        out.writeUTF(obf); //Obf Name
        out.writeUTF(srg); //SRG Name
        if (clean.length == 0) {
            out.writeBoolean(false); //Exists in clean
        } else {
            out.writeBoolean(true); //Exists in clean
            out.writeInt(adlerHash(clean));
        }
        out.writeInt(diff.length); //If removed, diff.length == 0
        out.write(diff);
        return out.toByteArray();
    }

    @InputFile
    public File getCleanJar() {
        return cleanJar;
    }
    public void setCleanJar(File value) {
        this.cleanJar = value;
    }

    @InputFile
    public File getDirtyJar() {
        return dirtyJar;
    }
    public void setDirtyJar(File value) {
        this.dirtyJar = value;
    }

    @InputFiles
    public Set<File> getPatchSets() {
        return this.patchSets;
    }
    public void addPatchSet(File value) {
        if (value != null) {
            this.patchSets.add(value);
        }
    }

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Input
    public String getSide() {
        return this.side;
    }
    public void setSide(String value) {
        this.side = value;
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/" + getSide() + ".lzma"));
        }
    }

    @OutputFile
    public File getOutput() {
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/output.lzma"));
        }
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

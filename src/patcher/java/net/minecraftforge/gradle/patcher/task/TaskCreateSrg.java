package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import joptsimple.internal.Strings;

public class TaskCreateSrg extends DefaultTask {
    private static Pattern CLS_ENTRY = Pattern.compile("L([^;]+);");

    private File srg;
    private File mappings;
    private boolean notch = false;
    private File output = getProject().file("build/" + getName() + "/output.srg");


    @TaskAction
    public void run() throws IOException {
        Map<String, String> names = loadMappings();
        List<String> out = new ArrayList<>();

        List<String> lines = Files.readLines(getSrg(), StandardCharsets.UTF_8);
        lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList()); //Strip enpty/comments

        Map<String, String> classes = new HashMap<>();
        lines.stream()
        .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
        .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
        .filter(pts -> pts.length == 2 && !pts[0].endsWith("/"))
        .forEach(pts -> classes.put(pts[0], pts[1]));

        lines.stream().map(l -> l.split(" ")).forEach(pts -> {
            boolean tsrg = false;
            if (pts[0].startsWith("\t")) {
                tsrg = true;
                pts[0] = pts[0].substring(1);
            }
            if (pts[0].indexOf(':') != -1) {
                if (pts[0].equals("PK:") || pts[0].equals("CL:")) {
                    if (notch) {
                        swap(pts, 1, 2);
                    } else {
                        pts[1] = pts[2]; //Classes stay the same
                    }
                } else if (pts[0].equals("FD:")) {
                    if (notch) {
                        swap(pts, 1, 2);
                        pts[1] = remapSrg(pts[1], names);
                    } else {
                        pts[1] = remapSrg(pts[2], names);
                    }
                } else if (pts[0].equals("MD:")) {
                    if (notch) {
                        swap(pts, 1, 3);
                        swap(pts, 2, 4);
                        pts[1] = remapSrg(pts[1], names);
                    } else {
                        pts[1] = remapSrg(pts[3], names);
                        pts[2] = pts[4];
                    }
                }
            } else if (tsrg) {
                if (pts.length == 2) { //OBF NAME
                    if (notch) {
                        swap(pts, 0, 1);
                        pts[0] = names.getOrDefault(pts[0], pts[0]);
                    } else {
                        pts[0] = names.getOrDefault(pts[1], pts[1]);
                    }
                } else if (pts.length == 3) { //OBF DESC NAME
                    if (notch) {
                        swap(pts, 0, 2);
                        pts[0] = names.getOrDefault(pts[0], pts[0]);
                    } else {
                        pts[0] = names.getOrDefault(pts[2], pts[2]);
                    }
                    pts[1] = remapDesc(pts[1], classes);
                } else {
                    throw new IllegalStateException("Invalid TSRG line: " + String.join(" ", pts));
                }
                pts[0] = '\t' + pts[0];
            } else {
                if (pts.length == 2) { //OBF NAME
                    if (notch) {
                        swap(pts, 0, 1);
                    } else {
                        pts[0] = pts[1];
                    }
                } else if (pts.length == 3) { // CLASS OBF NAME
                    pts[0] = classes.getOrDefault(pts[0], pts[0]);
                    if (notch) {
                        swap(pts, 1, 2);
                        pts[1] = names.getOrDefault(pts[1], pts[1]);
                    } else {
                        pts[1] = names.getOrDefault(pts[2], pts[2]);
                    }
                } else if (pts.length == 4) { //CLASS OBF DESC NAME
                    pts[0] = classes.getOrDefault(pts[0], pts[0]);
                    if (notch) {
                        swap(pts, 1, 3);
                        pts[1] = names.getOrDefault(pts[1], pts[1]);
                    } else {
                        pts[1] = names.getOrDefault(pts[3], pts[3]);
                    }
                    pts[2] = remapDesc(pts[2], classes);
                } else {
                    throw new IllegalStateException("Invalid CSRG line: " + String.join(" ", pts));
                }
            }
            out.add(String.join(" ", pts));
        });

        try (FileOutputStream fos = new FileOutputStream(getOutput())) {
            IOUtils.write(String.join("\n", out), fos, StandardCharsets.UTF_8);
        }
    }

    private String remapSrg(String entry, Map<String, String> names) {
        int idx = entry.lastIndexOf('/');
        String name = entry.substring(idx + 1);
        return entry.substring(0, idx + 1) + names.getOrDefault(name, name);
    }

    private String remapClass(String cls, Map<String, String> map)
    {
        String ret = map.get(cls);
        if (ret != null)
            return ret;

        int idx = cls.lastIndexOf('$');
        if (idx != -1)
            ret = remapClass(cls.substring(0, idx), map) + cls.substring(idx);
        else
            ret = cls;
        map.put(cls, ret);
        return cls;
    }

    private String remapDesc(String desc, Map<String, String> map)
    {
        StringBuffer buf = new StringBuffer();
        Matcher matcher = CLS_ENTRY.matcher(desc);
        while (matcher.find()) {
            matcher.appendReplacement(buf, Matcher.quoteReplacement("L" + remapClass(matcher.group(1), map) + ";"));
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    private Map<String, String> loadMappings() throws IOException {
        Map<String, String> names = new HashMap<>();
        try (ZipFile zip = new ZipFile(getMappings())) {
            zip.stream().filter(e -> e.getName().equals("fields.csv") || e.getName().equals("methods.csv")).forEach(e -> {
                CsvReader reader = new CsvReader();
                reader.setContainsHeader(true);
                try {
                    CsvContainer csv  = reader.read(new InputStreamReader(zip.getInputStream(e)));
                    for (CsvRow row : csv.getRows()) {
                        names.put(row.getField("searge"), row.getField("name"));
                    }
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            });
        }
        return names;
    }

    private void swap(String[] value, int i1, int i2) {
        String tmp = value[i1];
        value[i1] = value[i2];
        value[i2] = tmp;
    }

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @InputFile
    public File getMappings() {
        return mappings;
    }
    public void setMappings(File value) {
        this.mappings = value;
    }

    @Input
    public boolean getNotch() {
        return notch;
    }
    public void toNotch() {
        this.notch = true;
    }
    public void toSrg() {
        this.notch = false;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

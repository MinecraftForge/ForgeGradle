/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
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

package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Strings;
import com.google.common.io.Files;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;

public class TaskCreateExc extends DefaultTask {
    private static Pattern CLS_ENTRY = Pattern.compile("L([^;]+);");

    private File srg;
    private File statics;
    private File constructors;
    private File mappings;
    private File output = getProject().file("build/" + getName() + "/output.exc");

    @TaskAction
    public void run() throws IOException {
        Set<String> staticMap = new HashSet<>(Files.readLines(getStatics(), StandardCharsets.UTF_8));
        Map<String, String> names = loadMappings();
        List<String> out = new ArrayList<>();

        List<String> lines = Files.readLines(getSrg(), StandardCharsets.UTF_8);
        lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList()); //Strip empty/comments

        Map<String, String> classes = new HashMap<>();
        lines.stream()
        .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
        .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
        .filter(pts -> pts.length == 2 && !pts[0].endsWith("/"))
        .forEach(pts -> classes.put(pts[0], pts[1]));

        String currentClass = null;
        for (String line : lines) {
            if (line.startsWith("\t")) line = currentClass + " " + line.substring(1);

            String[] pts = line.split(" ");
            if (pts[0].indexOf(':') != -1) {
                if (pts[0].equals("MD:")) {
                    int idx = pts[3].lastIndexOf('/');
                    String name = pts[3].substring(idx + 1);
                    if (name.startsWith("func_") && !pts[4].contains("()")) {
                        out.add(pts[3].substring(0, idx + 1) + "." + names.getOrDefault(name, name) + pts[4] + "=|" + String.join(",", buildArgs(name, pts[4], staticMap.contains(name))));
                    }
                }
            } else {
                if (pts.length == 2) {
                    currentClass = pts[1];
                } else if (pts.length == 4) {
                    String name = pts[3];
                    if (name.startsWith("func_") && !pts[2].contains("()")) {
                        String desc = remapDesc(pts[2], classes);
                        out.add(currentClass + "." + names.getOrDefault(name, name) + desc + "=|" + String.join(",", buildArgs(name, desc, staticMap.contains(name))));
                    }
                }
            }
        }

        Files.readLines(getConstructors(), StandardCharsets.UTF_8).stream().map(l -> l.split(" ")).forEach(pts -> {
            out.add(pts[1] + ".<init>" + pts[2] + "=|" + String.join(",", buildArgs(pts[0], pts[2], false)));
        });

        try (FileOutputStream fos = new FileOutputStream(getOutput())) {
            IOUtils.write(String.join("\n", out), fos, StandardCharsets.UTF_8);
        }
    }

    private List<String> buildArgs(String name, String desc, boolean isStatic) {
        String prefix = "p_i" + name + "_";
        if (name.startsWith("func_")) {
            prefix = "p_" + name.split("_")[1] + "_";
        }
        List<String> ret = new ArrayList<String>();
        int idx = isStatic ? 0 : 1;
        int x = 1;
        while (desc.charAt(x) != ')') {
            int array = 0;
            while (desc.charAt(x) == '[') {
                x++;
                array++;
            }
            int size = 1;
            char type = desc.charAt(x);
            if (array == 0 && (type == 'D' || type == 'J')) //Long/Double's are 2 wide.
                size = 2;
            if (type == 'L')
                x = desc.indexOf(';', x);
            x++;

            ret.add(prefix + idx + '_');
            idx += size;
        }

        return ret;
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

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }
    @InputFile
    public File getStatics() {
        return this.statics;
    }
    public void setStatics(File value) {
        this.statics = value;
    }
    @InputFile
    public File getConstructors() {
        return this.constructors;
    }
    public void setConstructors(File value) {
        this.constructors = value;
    }
    @InputFile
    public File getMappings() {
        return mappings;
    }
    public void setMappings(File value) {
        this.mappings = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

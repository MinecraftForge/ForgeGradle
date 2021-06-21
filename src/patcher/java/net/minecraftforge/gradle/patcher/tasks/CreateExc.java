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

package net.minecraftforge.gradle.patcher.tasks;

import net.minecraftforge.gradle.common.config.MCPConfigV2;

import net.minecraftforge.srgutils.IMappingFile;
import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Strings;
import com.google.common.io.Files;
import de.siegmar.fastcsv.reader.NamedCsvReader;
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

public abstract class CreateExc extends DefaultTask {
    private static final Pattern CLS_ENTRY = Pattern.compile("L([^;]+);");

    public CreateExc() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.exc")));
    }

    @TaskAction
    public void run() throws IOException {
        MCPConfigV2 cfg = MCPConfigV2.getFromArchive(getConfig().get().getAsFile());
        if (cfg != null && cfg.isOfficial()) {
            // Write empty file if MCPConfig is official because it means TSRGv2
            try (FileOutputStream fos = new FileOutputStream(getOutput().get().getAsFile())) {
                IOUtils.write("", fos, StandardCharsets.UTF_8);
            }
            return;
        }

        Set<String> staticMap = new HashSet<>(Files.readLines(getStatics().get().getAsFile(), StandardCharsets.UTF_8));
        Map<String, String> names = loadMappings();
        List<String> out = new ArrayList<>();

        List<String> lines = Files.readLines(getSrg().get().getAsFile(), StandardCharsets.UTF_8);
        lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList()); //Strip empty/comments

        Map<String, String> classes = IMappingFile.load(getSrg().get().getAsFile()).getClasses().stream()
                .collect(Collectors.toMap(IMappingFile.IClass::getOriginal, IMappingFile.IClass::getMapped));

        String currentClass = null;
        for (String line : lines) {
            if (line.startsWith("\t")) line = currentClass + " " + line.substring(1);

            String[] pts = line.split(" ");
            if (pts[0].indexOf(':') != -1) {
                if (pts[0].equals("MD:")) {
                    int idx = pts[3].lastIndexOf('/');
                    String name = pts[3].substring(idx + 1);
                    if ((name.startsWith("func_") || name.startsWith("m_")) && !pts[4].contains("()")) {
                        out.add(pts[3].substring(0, idx + 1) + "." + names.getOrDefault(name, name) + pts[4] + "=|" + String.join(",", buildArgs(name, pts[4], staticMap.contains(name))));
                    }
                }
            } else {
                if (pts.length == 2) {
                    currentClass = pts[1];
                } else if (pts.length == 4) {
                    String name = pts[3];
                    if ((name.startsWith("func_") || name.startsWith("m_")) && !pts[2].contains("()")) {
                        String desc = remapDesc(pts[2], classes);
                        out.add(currentClass + "." + names.getOrDefault(name, name) + desc + "=|" + String.join(",", buildArgs(name, desc, staticMap.contains(name))));
                    }
                }
            }
        }

        Files.readLines(getConstructors().get().getAsFile(), StandardCharsets.UTF_8).stream()
                .map(l -> l.split(" "))
                .forEach(pts -> out.add(pts[1] + ".<init>" + pts[2] + "=|" + String.join(",", buildArgs(pts[0], pts[2], false))));

        try (FileOutputStream fos = new FileOutputStream(getOutput().get().getAsFile())) {
            IOUtils.write(String.join("\n", out), fos, StandardCharsets.UTF_8);
        }
    }

    private List<String> buildArgs(String name, String desc, boolean isStatic) {
        String prefix = "p_i" + name + "_";
        if (name.startsWith("func_") || name.startsWith("m_")) {
            prefix = "p_" + name.split("_")[1] + "_";
        }
        List<String> ret = new ArrayList<>();
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
        try (ZipFile zip = new ZipFile(getMappings().get().getAsFile())) {
            zip.stream().filter(e -> e.getName().equals("fields.csv") || e.getName().equals("methods.csv")).forEach(e -> {
                try (NamedCsvReader reader = NamedCsvReader.builder().build(new InputStreamReader(zip.getInputStream(e)))) {
                    reader.forEach(row -> names.put(row.getField("searge"), row.getField("name")));
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            });
        }
        return names;
    }

    @InputFile
    public abstract RegularFileProperty getConfig();

    @InputFile
    public abstract RegularFileProperty getSrg();

    @InputFile
    public abstract RegularFileProperty getStatics();

    @InputFile
    public abstract RegularFileProperty getConstructors();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

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

package net.minecraftforge.gradle.common.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import net.minecraftforge.gradle.common.util.Utils;

public class TaskApplyMappings extends DefaultTask {
    private static final Pattern SRG = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_\\b");

    private File mappings;
    private File input;
    private File output = getProject().file("build/" + getName() + "/output.zip");

    @TaskAction
    public void apply() throws IOException {
        Map<String, String> names = new HashMap<>();
        try (ZipFile zip = new ZipFile(getMappings())) {
            zip.stream().filter(e -> e.getName().endsWith(".csv")).forEach(e -> {
                CsvReader reader = new CsvReader();
                reader.setContainsHeader(true);
                try {
                    CsvContainer csv  = reader.read(new InputStreamReader(zip.getInputStream(e)));
                    for (CsvRow row : csv.getRows()) {
                        if (row.getField("searge") != null) {
                            names.put(row.getField("searge"), row.getField("name"));
                        } else {
                            names.put(row.getField("param"), row.getField("name"));
                        }
                    }
                } catch (IOException e1) {
                    throw new RuntimeException(e1);
                }
            });
        }

        try (ZipFile zin = new ZipFile(getInput())) {
            try (FileOutputStream fos = new FileOutputStream(getOutput());
                 ZipOutputStream out = new ZipOutputStream(fos)) {
                zin.stream().forEach(e -> {
                    try {
                        out.putNextEntry(Utils.getStableEntry(e.getName()));
                        if (!e.getName().endsWith(".java")) {
                            IOUtils.copy(zin.getInputStream(e), out);
                        } else {
                            out.write(replace(zin.getInputStream(e), names).getBytes(StandardCharsets.UTF_8));
                        }
                        out.closeEntry();
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }
        }
    }

    private String replace(InputStream stream, Map<String, String> names) throws IOException {
        StringWriter writer = new StringWriter();
        IOUtils.copy(stream, writer, StandardCharsets.UTF_8);
        StringBuffer buf = new StringBuffer();
        Matcher matcher = SRG.matcher(writer.toString());
        while (matcher.find()) {
            String name = names.get(matcher.group());
            matcher.appendReplacement(buf, name == null ? matcher.group() : name);
        }
        matcher.appendTail(buf);
        return buf.toString();
    }


    @InputFile
    public File getInput() {
        return input;
    }

    @InputFile
    public File getMappings() {
        return mappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setInput(File clean) {
        input = clean;
    }

    public void setMappings(File value) {
        mappings = value;
    }

    public void setOutput(File value) {
        output = value;
    }
}

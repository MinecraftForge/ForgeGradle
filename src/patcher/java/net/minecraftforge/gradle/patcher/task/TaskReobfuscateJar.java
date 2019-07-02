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

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class TaskReobfuscateJar extends DefaultTask {

    private String tool = Utils.SPECIALSOURCE;
    private String[] args = new String[] {"--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{srg}", "--live"};
    private FileCollection classpath = null;
    private File input;
    private File srg;
    //TODO: Extra SRGs
    private boolean keepPackages = false;
    private boolean keepData = false;
    private File output = getProject().file("build/" + getName() + "/output.jar");
    private File output_temp = getProject().file("build/" + getName() + "/output_temp.jar");

    @TaskAction
    public void apply() throws IOException {
        File jar = MavenArtifactDownloader.gradle(getProject(), getTool(), false);

        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", output_temp.getAbsolutePath());
        replace.put("{srg}", getSrg().getAbsolutePath());

        List<String> _args = new ArrayList<>();
        for (String arg : args) {
            _args.add(replace.getOrDefault(arg, arg));
        }

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        File workDir = getProject().file("build/" + getName());
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        JavaExec java = getProject().getTasks().create("_", JavaExec.class);
        try (OutputStream log = new BufferedOutputStream(new FileOutputStream(new File(workDir, "log.txt")))) {
            // Execute command
            java.setArgs(_args);
            if (getClasspath() == null) {
                java.setClasspath(getProject().files(jar));
            } else {
                java.setClasspath(getProject().files(getClasspath(), jar));
            }
            java.setWorkingDir(workDir);
            java.setMain(mainClass);
            java.setStandardOutput(new OutputStream() {
                @Override
                public void flush() throws IOException {
                    log.flush();
                }
                @Override
                public void close() {}
                @Override
                public void write(int b) throws IOException {
                    log.write(b);
                }
            });
            java.exec();

            List<String> lines = Files.readLines(getSrg(), StandardCharsets.UTF_8);
            lines = lines.stream().map(line -> line.split("#")[0]).filter(l -> l != null & !l.trim().isEmpty()).collect(Collectors.toList()); //Strip empty/comments

            Set<String> packages = new HashSet<>();
            lines.stream()
            .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:")))
            .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" "))
            .filter(pts -> pts.length == 2)
            .forEach(pts -> {
                int idx = pts[0].lastIndexOf('/');
                if (idx != -1) {
                    packages.add(pts[0].substring(0, idx + 1) + "package-info.class");
                }
            });

            try (ZipFile zin = new ZipFile(output_temp);
                 ZipOutputStream out = new ZipOutputStream(new FileOutputStream(getOutput()))) {
                for (Enumeration<? extends ZipEntry> enu = zin.entries(); enu.hasMoreElements(); ) {
                    ZipEntry entry = enu.nextElement();
                    boolean filter = entry.isDirectory() || entry.getName().startsWith("mcp/"); //Directories and MCP's annotations
                    if (!keepPackages) filter |= packages.contains(entry.getName());
                    if (!keepData) filter |= !entry.getName().endsWith(".class");

                    if (filter) {
                        log.write(("Filtered: " + entry.getName() + '\n').getBytes(StandardCharsets.UTF_8));
                        continue;
                    }
                    out.putNextEntry(entry);
                    IOUtils.copy(zin.getInputStream(entry), out);
                    out.closeEntry();
                }
            }

            output_temp.delete();
        } finally {
            getProject().getTasks().remove(java);
        }
    }

    @Input
    public String getTool() {
        return this.tool;
    }
    public void setTool(String value) {
        this.tool = value;
    }

    @Input
    public String[] getArgs() {
        return this.args;
    }
    public void setArgs(String[] value) {
        this.args = value;
    }

    @Input
    public boolean getKeepPackages() {
        return this.keepPackages;
    }
    public void keepPackages() {
        this.keepPackages = true;
    }
    public void filterPackages() {
        this.keepPackages = false;
    }

    @Input
    public boolean getKeepData() {
        return this.keepData;
    }
    public void keepData() {
        this.keepData = true;
    }
    public void filterData() {
        this.keepData = false;
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @InputFile
    public File getSrg() {
        return srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Optional
    @InputFiles
    public FileCollection getClasspath() {
        return this.classpath;
    }
    public void setClasspath(FileCollection value) {
        this.classpath = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

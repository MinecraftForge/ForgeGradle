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

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SideAnnotationStripperFunction implements MCPFunction {
    private List<File> files;
    private String data;

    public SideAnnotationStripperFunction(Project mcp, List<File> files) {
        this.files = files;
    }

    @Override
    public File execute(MCPEnvironment env) throws IOException {

        File input = (File)env.getArguments().get("input");
        File output = env.getFile("output.jar");
        File cacheFile = env.getFile("lastinput.sha1");
        HashStore cache = new HashStore(env.project).load(cacheFile);
        cache.add(files);
        if (data != null)
            cache.add("data", data);
        if (cache.isSame() && output.exists()) return output;

        Set<String> classes = new HashSet<>();
        Set<String> methods = new HashSet<>();
        Consumer<String> lineReader = line -> {
            int idx = line.indexOf('#');
            if (idx == 0 || line.isEmpty()) return;
            if (idx != -1) line = line.substring(0, idx - 1);
            if (line.charAt(0) == '\t') line = line.substring(1);
            String[] pts = (line.trim() + "    ").split(" ", -1);
            classes.add(pts[0]);
            if (pts.length > 1)
                methods.add(pts[0] + ' ' + pts[1]);
            else
                methods.add(pts[0]);
        };
        for (File file : files)
            FileUtils.readLines(file).forEach(lineReader);
        if (data != null) {
            for (String line : data.split("\n"))
                lineReader.accept(line);
        }


        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        output.createNewFile();

        try (ZipInputStream  zis = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(new ZipEntry(entry.getName()));
                if (!entry.getName().endsWith(".class") || !classes.contains(entry.getName().substring(0, entry.getName().length() - 6))) {
                    int read;
                    byte[] buf = new byte[0x100];
                    while ((read = zis.read(buf, 0, buf.length)) != -1)
                        zos.write(buf, 0, read);
                } else {
                    ClassReader reader = new ClassReader(zis);
                    ClassNode node = new ClassNode();
                    reader.accept(node, 0);

                    if (node.methods != null) {
                        node.methods.forEach(mtd -> {
                            if (methods.contains(node.name + ' ' + mtd.name + mtd.desc)) {
                                if (mtd.visibleAnnotations != null) {
                                    Iterator<AnnotationNode> itr = mtd.visibleAnnotations.iterator();
                                    while (itr.hasNext()) {
                                        AnnotationNode ann = itr.next();
                                        if ("Lnet/minecraftforge/api/distmarker/OnlyIn;".equals(ann.desc))
                                            itr.remove();
                                    }
                                }
                            }
                        });
                    }

                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    zos.write(writer.toByteArray());
                }
                zos.closeEntry();
            }
        }
        cache.save(cacheFile);
        return output;
    }


    public void addData(String data) {
        if (this.data == null) this.data = data;
        else this.data += "\n#============================================================\n" + data;
    }

    @Override
    public void addInputs(HashStore cache, String prefix) {
        cache.add(files);
        if (data != null)
            cache.add(prefix + "data", data);
    }
}

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

package net.minecraftforge.gradle.common.util;

import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EclipseHacks {

    @SuppressWarnings({"unchecked", "UnstableApiUsage"})
    public static void doEclipseFixes(@Nonnull final MinecraftExtension minecraft, @Nonnull final ExtractNatives nativesTask, @Nonnull final DownloadAssets assetsTask, @Nonnull final TaskProvider<Task> makeSrcDirs) {
        final Project project = minecraft.getProject();
        final File natives = nativesTask.getOutput();
        final File assets = assetsTask.getOutput();

        final String LIB_ATTR = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";

        project.getTasks().withType(GenerateEclipseClasspath.class, task -> {
            task.dependsOn(nativesTask, assetsTask, makeSrcDirs.get());

            task.doLast(t -> {
                try {
                    Node xml = new XmlParser().parse(task.getOutputFile());

                    List<Node> entries = (ArrayList<Node>)xml.get("classpathentry");
                    Set<String> paths = new HashSet<>();
                    List<Node> remove = new ArrayList<>();

                    entries.stream().filter(e -> "src".equals(e.get("@kind"))).forEach(e -> {
                        if (!paths.add((String)e.get("@path"))) { //Eclipse likes to duplicate things... No idea why, lets kill them off
                            remove.add(e);
                        }

                        if (((List<Node>)e.get("attributes")).isEmpty()) {
                            e.appendNode("attributes");
                        }

                        Node attr = ((List<Node>)e.get("attributes")).get(0);

                        if (((List<Node>)attr.get("attribute")).stream().noneMatch(n -> LIB_ATTR.equals(n.get("@name")))) {
                            attr.appendNode("attribute", props("name", LIB_ATTR, "value", natives.getAbsolutePath()));
                        }
                    });

                    remove.forEach(xml::remove);

                    try (OutputStream fos = new FileOutputStream(task.getOutputFile())) {
                        IOUtils.write(XmlUtil.serialize(xml), fos, StandardCharsets.UTF_8);
                    }
                } catch (IOException | SAXException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
            });
        });
    }

    private static Map<String, String> props(String... data) {
        if (data.length % 2 != 0) {
            throw new IllegalArgumentException("Properties must be key,value pairs");
        }
        Map<String, String> ret = new HashMap<>();
        for (int x = 0; x < data.length; x += 2) {
            ret.put(data[x], data[x + 1]);
        }
        return ret;
    }
}

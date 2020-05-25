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

package net.minecraftforge.gradle.userdev.util;

import net.minecraftforge.gradle.common.util.*;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.userdev.tasks.RenameJarSrg2Mcp;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Deobfuscator {
    private final Project project;
    private File cacheRoot;

    private DocumentBuilder xmlParser;
    private XPath xPath;
    private Transformer xmlTransformer;

    public Deobfuscator(Project project, File cacheRoot) {
        this.project = project;
        this.cacheRoot = cacheRoot;

        try {
            xPath = XPathFactory.newInstance().newXPath();
            xmlParser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            xmlTransformer = TransformerFactory.newInstance().newTransformer();
        } catch (ParserConfigurationException | TransformerConfigurationException e) {
            throw new RuntimeException("Error configuring XML parsers", e);
        }
    }

    public File deobfPom(File original, String mappings, String... cachePath) throws IOException {
        project.getLogger().debug("Updating POM file {} with mappings {}", original.getName(), mappings);

        File output = getCacheFile(cachePath);
        File input = new File(output.getParent(), output.getName() + ".input");

        HashStore cache = new HashStore()
                .load(input)
                .add("mappings", mappings)
                .add("orig", original);

        if (!cache.isSame() || !output.exists()) {
            try {
                Document pom = xmlParser.parse(original);
                NodeList versionNodes = (NodeList) xPath.compile("/*[local-name()=\"project\"]/*[local-name()=\"version\"]").evaluate(pom, XPathConstants.NODESET);
                if (versionNodes.getLength() > 0) {
                    versionNodes.item(0).setTextContent(versionNodes.item(0).getTextContent() + "_mapped_" + mappings);
                }

                xmlTransformer.transform(new DOMSource(pom), new StreamResult(output));
            } catch (IOException | SAXException | XPathExpressionException | TransformerException e) {
                project.getLogger().error("Error attempting to modify pom file " + original.getName(), e);
                return original;
            }

            Utils.updateHash(output, HashFunction.SHA1);
            cache.save();
        }

        return output;
    }

    public File deobfBinary(File original, String mappings, String... cachePath) throws IOException {
        project.getLogger().debug("Deobfuscating binary file {} with mappings {}", original.getName(), mappings);

        File names = findMapping(mappings);
        if (names == null || !names.exists()) {
            return null;
        }

        File output = getCacheFile(cachePath);
        File input = new File(output.getParent(), output.getName() + ".input");

        HashStore cache = new HashStore()
                .load(input)
                .add("names", names)
                .add("orig", original);

        if (!cache.isSame() || !output.exists()) {
            RenameJarSrg2Mcp rename = project.getTasks().create("_RenameSrg2Mcp_" + new Random().nextInt(), RenameJarSrg2Mcp.class);
            rename.setInput(original);
            rename.setOutput(output);
            rename.setMappings(names);
            rename.apply();
            project.getTasks().remove(rename);

            Utils.updateHash(output, HashFunction.SHA1);
            cache.save();
        }

        return output;
    }

    public File deobfSources(File original, String mappings, String... cachePath) throws IOException {
        project.getLogger().debug("Deobfuscating sources file {} with mappings {}", original.getName(), mappings);

        File names = findMapping(mappings);
        if (names == null || !names.exists()) {
            return null;
        }

        File output = getCacheFile(cachePath);

        File input = new File(output.getParent(), output.getName() + ".input");

        HashStore cache = new HashStore()
                .load(input)
                .add("names", names)
                .add("orig", original);

        if (!cache.isSame() || !output.exists()) {
            McpNames map = McpNames.load(names);

            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output))) {
                ZipEntry _old;
                while ((_old = zin.getNextEntry()) != null) {
                    zout.putNextEntry(Utils.getStableEntry(_old.getName()));

                    if (_old.getName().endsWith(".java")) {
                        String mapped = map.rename(zin, false);
                        IOUtils.write(mapped, zout);
                    } else {
                        IOUtils.copy(zin, zout);
                    }
                }
            }

            Utils.updateHash(output, HashFunction.SHA1);
            cache.save();
        }

        return output;
    }

    private File getCacheFile(String... cachePath) {
        File cacheFile = new File(cacheRoot, String.join(File.separator, cachePath));
        cacheFile.getParentFile().mkdirs();
        return cacheFile;
    }

    private File findMapping(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = MCPRepo.getMappingDep(channel, version);
        return MavenArtifactDownloader.generate(project, desc, false);
    }
}

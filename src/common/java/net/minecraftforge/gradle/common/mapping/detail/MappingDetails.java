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

package net.minecraftforge.gradle.common.mapping.detail;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.siegmar.fastcsv.reader.NamedCsvReader;
import de.siegmar.fastcsv.reader.NamedCsvRow;
import net.minecraftforge.gradle.common.mapping.util.Sides;
import net.minecraftforge.gradle.common.mapping.util.MappingStreams;
import net.minecraftforge.srgutils.IMappingFile;

import static net.minecraftforge.gradle.common.mapping.detail.IMappingDetail.*;

public class MappingDetails {

    /**
     * Converts two {@link IMappingFile}s into an {@link IMappingDetail} with {@link INode#getSide} calculated based on the inputs
     */
    public static IMappingDetail fromSrg(IMappingFile client, IMappingFile server) {
        Map<String, INode> classes = new HashMap<>();
        Map<String, INode> fields = new HashMap<>();
        Map<String, INode> methods = new HashMap<>();
        Map<String, INode> params = new HashMap<>();

        forEach(client, server, MappingDetails::forEachClass, classes::put);
        forEach(client, server, MappingDetails::forEachFields, fields::put);
        forEach(client, server, MappingDetails::forEachMethod, methods::put);
        forEach(client, server, MappingDetails::forEachParam, params::put);

        return new MappingDetail(classes, fields, methods, params);
    }

    /**
     * Converts a {@link IMappingFile} into an {@link IMappingDetail}
     */
    public static IMappingDetail fromSrg(IMappingFile input) {
        Map<String, INode> classes = new HashMap<>();
        Map<String, INode> fields = new HashMap<>();
        Map<String, INode> methods = new HashMap<>();
        Map<String, INode> params = new HashMap<>();

        forEachClass(input, classes::put);
        forEachFields(input, fields::put);
        forEachMethod(input, methods::put);
        forEachParam(input, params::put);

        return new MappingDetail(classes, fields, methods, params);
    }

    /**
     * Converts a `mapping.zip` into an {@link IMappingDetail}
     */
    public static IMappingDetail fromZip(File input) throws IOException {
        try (ZipFile zip = new ZipFile(input)) {
            Map<String, INode> classes = readEntry(zip, "classes.csv");
            Map<String, INode> fields = readEntry(zip, "fields.csv");
            Map<String, INode> methods = readEntry(zip, "methods.csv");
            Map<String, INode> params = readEntry(zip, "params.csv");

            return new MappingDetail(classes, fields, methods, params);
        }
    }

    /**
     * Classes are represented in `.` form in the `mappings.zip and with '/' in FG/SRG
     * @see #decodeClass
     */
    public static String encodeClass(String name) {
        return name.replace("/", ".");
    }

    /**
     * Line breaks in encoded Javadocs are represented as literal \n
     * @see #decodeJavadoc
     */
    public static String encodeJavadoc(String javadoc) {
        return javadoc.replaceAll("\r?\n", "\\n");
    }

    /**
     * Classes are represented in `.` form in the `mappings.zip and with '/' in FG/SRG
     * @see #encodeClass
     */
    public static String decodeClass(String name) {
        return name.replace(".", "/");
    }

    /**
     * Line breaks in encoded Javadocs are represented as literal \n
     * @see #encodeJavadoc
     */
    public static String decodeJavadoc(String javadoc) {
        return javadoc.replaceAll("\\n", "\n");
    }

    private static void forEach(IMappingFile client, IMappingFile server, BiConsumer<IMappingFile, BiConsumer<String, INode>> iterator, BiConsumer<String, INode> consumer) {
        Map<String, INode> clientNodes = new HashMap<>();
        Map<String, INode> serverNodes = new HashMap<>();

        iterator.accept(client, clientNodes::put);
        iterator.accept(server, serverNodes::put);

        Set<String> clientKeys = clientNodes.keySet();
        Set<String> serverKeys = clientNodes.keySet();

        // Calculate Intersection between Client and Server
        Set<String> bothKeys = new HashSet<>(clientKeys);
        bothKeys.retainAll(serverNodes.keySet());
        Map<String, INode> bothNodes = new TreeMap<>();
        bothKeys.forEach(key -> bothNodes.put(key, clientNodes.get(key)));

        // Remove Both from Client / Server
        clientKeys.removeAll(bothKeys);
        serverKeys.removeAll(bothKeys);

        // Provide upwards
        clientNodes.values().stream().map(it -> it.withSide(Sides.CLIENT)).forEach(node -> consumer.accept(node.getOriginal(), node));
        serverNodes.values().stream().map(it -> it.withSide(Sides.SERVER)).forEach(node -> consumer.accept(node.getOriginal(), node));
        bothNodes.values().stream().map(it -> it.withSide(Sides.BOTH)).forEach(node -> consumer.accept(node.getOriginal(), node));
    }

    private static void forEachClass(IMappingFile input, BiConsumer<String, INode> consumer) {
        MappingStreams.classes(input).map(INode::of).forEach(node -> consumer.accept(node.getOriginal(), node));
    }

    private static void forEachFields(IMappingFile input, BiConsumer<String, INode> consumer) {
        MappingStreams.fields(input).map(INode::of).forEach(node -> consumer.accept(node.getOriginal(), node));
    }

    private static void forEachMethod(IMappingFile input, BiConsumer<String, INode> consumer) {
        MappingStreams.methods(input).map(INode::of).forEach(node -> consumer.accept(node.getOriginal(), node));
    }

    private static void forEachParam(IMappingFile input, BiConsumer<String, INode> consumer) {
        MappingStreams.parameters(input).map(INode::of).forEach(node -> consumer.accept(node.getOriginal(), node));
    }

    private static Map<String, INode> readEntry(ZipFile zip, String entryName) throws IOException {
        Optional<? extends ZipEntry> entry = zip.stream().filter(e -> Objects.equals(entryName, e.getName())).findFirst();

        if (!entry.isPresent()) return Collections.emptyMap();

        Map<String, INode> nodes = new HashMap<>();

        try (NamedCsvReader reader = NamedCsvReader.builder().build(new InputStreamReader(zip.getInputStream(entry.get())))) {
            Set<String> headers = reader.getHeader();
            String obf = headers.contains("searge") ? "searge" : "param";

            reader.forEach(row -> {
                String obfuscated = decodeClass(row.getField(obf));
                String name = decodeClass(get(headers, row, "name", obfuscated));
                String side = get(headers, row, "side", Sides.BOTH);
                String javadoc = decodeJavadoc(get(headers, row, "desc", ""));

                nodes.put(obfuscated, INode.of(obfuscated, name, side, javadoc));
            });
        }

        return nodes;
    }

    private static String get(Set<String> headers, NamedCsvRow row, String name, String defaultValue) {
        return headers.contains(name) ? row.getField(name) : defaultValue;
    }
}
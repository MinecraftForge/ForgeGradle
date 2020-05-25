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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.CharStreams;

import de.siegmar.fastcsv.reader.CsvContainer;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRow;
import org.apache.commons.lang3.tuple.Pair;

public class McpNames {
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final Pattern SRG_FINDER             = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_\\b");
    private static final Pattern METHOD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern FIELD_JAVADOC_PATTERN  = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?:\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");
    private static final Pattern CLASS_JAVADOC_PATTERN  = Pattern.compile("^(?<indent>(?: )*|\\t*)([\\w|@]*\\s)*(class|interface|@interface|enum) (?<name>[\\w]+)");
    private static final Pattern CLOSING_CURLY_BRACE    = Pattern.compile("^(?<indent>(?: )*|\\t*)}");
    private static final Pattern PACKAGE_DECL           = Pattern.compile("^[\\s]*package(\\s)*(?<name>[\\w|.]+);$");

    public static McpNames load(File data) throws IOException {
        Map<String, String> names = new HashMap<>();
        Map<String, String> docs = new HashMap<>();
        try (ZipFile zip = new ZipFile(data)) {
            List<ZipEntry> entries = zip.stream().filter(e -> e.getName().endsWith(".csv")).collect(Collectors.toList());
            for (ZipEntry entry : entries) {
                CsvReader reader = new CsvReader();
                reader.setContainsHeader(true);
                CsvContainer csv = reader.read(new InputStreamReader(zip.getInputStream(entry)));
                for (CsvRow row : csv.getRows()) {
                    String searge = row.getField("searge");
                    if (searge == null)
                        searge = row.getField("param");
                    String desc = row.getField("desc");
                    names.put(searge, row.getField("name"));
                    if (desc != null)
                        docs.put(searge, desc);
                }
            }
        }

        return new McpNames(HashFunction.SHA1.hash(data), names, docs);
    }

    private Map<String, String> names;
    private Map<String, String> docs;
    public final String hash;

    private McpNames(String hash, Map<String, String> names, Map<String, String> docs) {
        this.hash = hash;
        this.names = names;
        this.docs = docs;
    }

    public String rename(InputStream stream, boolean javadocs) throws IOException {
        List<String> lines = new ArrayList<>();
        Deque<Pair<String, Integer>> innerClasses = new LinkedList<>(); //pair of inner class name & indentation
        String _package = ""; //default package
        for (String line : CharStreams.readLines(new InputStreamReader(stream))) {
            Matcher m = PACKAGE_DECL.matcher(line);
            if(m.find())
                _package = m.group("name") + ".";

            if (javadocs) {
                if (!injectJavadoc(lines, line, _package, innerClasses))
                    javadocs = false;
            }
            lines.add(replaceInLine(line));
        }
        return Joiner.on(NEWLINE).join(lines);
    }

    public String rename(String entry) {
        return names.getOrDefault(entry, entry);
    }

    /**
     * Injects a javadoc into the given list of lines, if the given line is a
     * method or field declaration.
     * @param lines The current file content (to be modified by this method)
     * @param line The line that was just read (will not be in the list)
     * @param _package the name of the package this file is declared to be in, in com.example format;
     * @param innerClasses current position in inner class
     */
    private boolean injectJavadoc(List<String> lines, String line, String _package, Deque<Pair<String, Integer>> innerClasses) {
        // methods
        Matcher matcher = METHOD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            String javadoc = docs.get(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));

            // worked, so return and don't try the fields.
            return true;
        }

        // fields
        matcher = FIELD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            String javadoc = docs.get(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, false));

            return true;
        }

        //classes
        matcher = CLASS_JAVADOC_PATTERN.matcher(line);
        if(matcher.find()) {
            //we maintain a stack of the current (inner) class in com.example.ClassName$Inner format (along with indentation)
            //if the stack is not empty we are entering a new inner class
            String currentClass = (innerClasses.isEmpty() ? _package : innerClasses.peek().getLeft() + "$") + matcher.group("name");
            innerClasses.push(Pair.of(currentClass, matcher.group("indent").length()));
            String javadoc = docs.get(currentClass);
            if (!Strings.isNullOrEmpty(javadoc)) {
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));
            }

            return true;
        }

        //detect curly braces for inner class stacking/end identification
        matcher = CLOSING_CURLY_BRACE.matcher(line);
        if(matcher.find()){
            if(!innerClasses.isEmpty()) {
                int len = matcher.group("indent").length();
                if (len == innerClasses.peek().getRight()) {
                    innerClasses.pop();
                } else if (len < innerClasses.peek().getRight()) {
                    System.err.println("Failed to properly track class blocks around class " + innerClasses.peek().getLeft() + ":" + (lines.size() + 1));
                    return false;
                }
            }
        }

        return true;
    }

    /** Inserts the given javadoc line into the list of lines before any annotations */
    private static void insertAboveAnnotations(List<String> list, String line) {
        int back = 0;
        while (list.get(list.size() - 1 - back).trim().startsWith("@"))
            back++;
        list.add(list.size() - back, line);
    }

    private String replaceInLine(String line) {
        StringBuffer buf = new StringBuffer();
        Matcher matcher = SRG_FINDER.matcher(line);
        while (matcher.find())
            matcher.appendReplacement(buf, names.getOrDefault(matcher.group(), matcher.group()));
        matcher.appendTail(buf);
        return buf.toString();
    }
}

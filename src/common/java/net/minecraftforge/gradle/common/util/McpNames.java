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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;

import de.siegmar.fastcsv.reader.NamedCsvReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.annotation.Nullable;

public class McpNames {
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final Pattern SRG_FINDER             = Pattern.compile("[fF]unc_\\d+_[a-zA-Z_]+|m_\\d+_|[fF]ield_\\d+_[a-zA-Z_]+|f_\\d+_|p_\\w+_\\d+_|p_\\d+_");
    private static final Pattern CONSTRUCTOR_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(public |private|protected |)(?<generic><[\\w\\W]*>\\s+)?(?<name>[\\w.]+)\\((?<parameters>.*)\\)\\s+(?:throws[\\w.,\\s]+)?\\{");
    private static final Pattern METHOD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>(?:func_|m_)[0-9]+_[a-zA-Z_]*)\\(");
    private static final Pattern FIELD_JAVADOC_PATTERN  = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*\\s+(?<name>(?:field_|f_)[0-9]+_[a-zA-Z_]*) *[=;]");
    private static final Pattern CLASS_JAVADOC_PATTERN  = Pattern.compile("^(?<indent> *|\\t*)([\\w|@]*\\s)*(class|interface|@interface|enum) (?<name>[\\w]+)");
    private static final Pattern CLOSING_CURLY_BRACE    = Pattern.compile("^(?<indent> *|\\t*)}");
    private static final Pattern PACKAGE_DECL           = Pattern.compile("^[\\s]*package(\\s)*(?<name>[\\w|.]+);$");
    private static final Pattern LAMBDA_DECL            = Pattern.compile("\\((?<args>(?:(?:, ){0,1}p_[\\w]+_\\d+_\\b)+)\\) ->");

    public static McpNames load(File data) throws IOException {
        Map<String, String> names = new HashMap<>();
        Map<String, String> docs = new HashMap<>();
        try (ZipFile zip = new ZipFile(data)) {
            List<ZipEntry> entries = zip.stream().filter(e -> e.getName().endsWith(".csv")).collect(Collectors.toList());
            for (ZipEntry entry : entries) {
                try (NamedCsvReader reader = NamedCsvReader.builder().build(new InputStreamReader(zip.getInputStream(entry)))) {
                    String obf = reader.getHeader().contains("searge") ? "searge" : "param";
                    boolean hasDesc = reader.getHeader().contains("desc");
                    reader.forEach(row -> {
                        String searge = row.getField(obf);
                        names.put(searge, row.getField("name"));
                        if (hasDesc) {
                            String desc = row.getField("desc");
                            if (!desc.isEmpty())
                                docs.put(searge, desc);
                        }
                    });
                }
            }
        }

        return new McpNames(HashFunction.SHA1.hash(data), names, docs);
    }

    private final Map<String, String> names;
    private final Map<String, String> docs;
    public final String hash;

    private McpNames(String hash, Map<String, String> names, Map<String, String> docs) {
        this.hash = hash;
        this.names = names;
        this.docs = docs;
    }

    public String rename(InputStream stream, boolean javadocs) throws IOException {
        return rename(stream, javadocs, true, StandardCharsets.UTF_8);
    }

    public String rename(InputStream stream, boolean javadocs, boolean lambdas) throws IOException {
        return rename(stream, javadocs, lambdas, StandardCharsets.UTF_8);
    }

    public String rename(InputStream stream, boolean javadocs, boolean lambdas, Charset sourceFileCharset)
            throws IOException {

        String data = IOUtils.toString(stream, sourceFileCharset);
        List<String> input = IOUtils.readLines(new StringReader(data));

        // Return early on emtpy files
        if (data.isEmpty())
            return "";

        //Reader doesn't give us the empty line if the file ends with a newline.. so add one.
        if (data.charAt(data.length() - 1) == '\r' || data.charAt(data.length() - 1) == '\n')
            input.add("");

        List<String> lines = new ArrayList<>();
        Deque<Pair<String, Integer>> innerClasses = new LinkedList<>(); //pair of inner class name & indentation
        String _package = ""; //default package
        Set<String> blacklist = null;

        if (!lambdas) {
            blacklist = new HashSet<>();
            for (String line : input) {
                Matcher m = LAMBDA_DECL.matcher(line);
                if (!m.find())
                    continue;
                blacklist.addAll(Arrays.asList(m.group("args").split(", ")));
            }
        }

        for (String line : input) {
            Matcher m = PACKAGE_DECL.matcher(line);
            if(m.find())
                _package = m.group("name") + ".";

            if (javadocs) {
                if (!injectJavadoc(lines, line, _package, innerClasses))
                    javadocs = false;
            }
            lines.add(replaceInLine(line, blacklist));
        }
        return String.join(NEWLINE, lines);
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
        // constructors
        Matcher matcher = CONSTRUCTOR_JAVADOC_PATTERN.matcher(line);
        boolean isConstructor = matcher.find() && !innerClasses.isEmpty() && innerClasses.peek().getLeft().contains(matcher.group("name"));
        // methods
        if (!isConstructor)
            matcher = METHOD_JAVADOC_PATTERN.matcher(line);

        if (isConstructor || matcher.find()) {
            String name = isConstructor ? "<init>" : matcher.group("name");
            String javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("func_") && !name.startsWith("m_")) {
                String currentClass = innerClasses.peek().getLeft();
                javadoc = docs.get(currentClass + '#' + name);
            }
            if (javadoc != null)
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));

            // worked, so return and don't try the fields.
            return true;
        }

        // fields
        matcher = FIELD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            String name = matcher.group("name");
            String javadoc = docs.get(name);
            if (javadoc == null && !innerClasses.isEmpty() && !name.startsWith("field_") && !name.startsWith("f_")) {
                String currentClass = innerClasses.peek().getLeft();
                javadoc = docs.get(currentClass + '#' + name);
            }
            if (javadoc != null)
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
            if (javadoc != null) {
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

    /*
     * There are certain times, such as Mixin Accessors that we wish to have the name of this method with the first character upper case.
     */
    private String getMapped(String srg, @Nullable Set<String> blacklist) {
        if (blacklist != null && blacklist.contains(srg))
            return srg;

        boolean cap = srg.charAt(0) == 'F';
        if (cap)
            srg = 'f' + srg.substring(1);

        String ret = names.getOrDefault(srg, srg);
        if (cap)
            ret = ret.substring(0, 1).toUpperCase(Locale.ENGLISH) + ret.substring(1);
        return ret;
    }

    private String replaceInLine(String line, @Nullable Set<String> blacklist) {
        StringBuffer buf = new StringBuffer();
        Matcher matcher = SRG_FINDER.matcher(line);
        while (matcher.find()) {
            // Since '$' is a valid character in identifiers, but we need to NOT treat this as a regex group, escape any occurrences
            matcher.appendReplacement(buf, Matcher.quoteReplacement(getMapped(matcher.group(), blacklist)));
        }
        matcher.appendTail(buf);
        return buf.toString();
    }
}

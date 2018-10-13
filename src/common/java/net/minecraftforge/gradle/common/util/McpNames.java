package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
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

public class McpNames {
    private static final String NEWLINE = System.getProperty("line.separator");
    private static final Pattern SRG_FINDER             = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_\\b");
    private static final Pattern METHOD_JAVADOC_PATTERN = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?<generic><[\\w\\W]*>\\s+)?(?<return>\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern FIELD_JAVADOC_PATTERN  = Pattern.compile("^(?<indent>(?: {3})+|\\t+)(?!return)(?:\\w+\\s+)*(?:\\w+[\\w$.]*(?:<[\\w\\W]*>)?[\\[\\]]*)\\s+(?<name>field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");

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
        for (String line : CharStreams.readLines(new InputStreamReader(stream))) {
            if (javadocs)
                injectJavadoc(lines, line);
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
     *
     * @param lines The current file content (to be modified by this method)
     * @param line The line that was just read (will not be in the list)
     * @param methodFunc A function that takes a method SRG id and returns its javadoc
     * @param fieldFunc A function that takes a field SRG id and returns its javadoc
     */
    private void injectJavadoc(List<String> lines, String line) {
        // methods
        Matcher matcher = METHOD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            String javadoc = docs.get(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, true));

            // worked, so return and don't try the fields.
            return;
        }

        // fields
        matcher = FIELD_JAVADOC_PATTERN.matcher(line);
        if (matcher.find()) {
            String javadoc = docs.get(matcher.group("name"));
            if (!Strings.isNullOrEmpty(javadoc))
                insertAboveAnnotations(lines, JavadocAdder.buildJavadoc(matcher.group("indent"), javadoc, false));
        }
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

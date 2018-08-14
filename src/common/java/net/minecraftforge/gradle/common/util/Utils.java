package net.minecraftforge.gradle.common.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {

    public static void extractFile(ZipFile zip, String name, File output) throws IOException {
        extractFile(zip, zip.getEntry(name), output);
    }

    public static void extractFile(ZipFile zip, ZipEntry entry, File output) throws IOException {
        File parent = output.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        try (InputStream stream = zip.getInputStream(entry)) {
            Files.copy(stream, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void extractDirectory(Function<String, File> fileLocator, ZipFile zip, String directory) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;
            if (!e.getName().startsWith(directory)) continue;
            extractFile(zip, e, fileLocator.apply(e.getName()));
        }
    }

    public static byte[] base64DecodeStringList(List<String> strings) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String string : strings) {
            bos.write(Base64.getDecoder().decode(string));
        }
        return bos.toByteArray();
    }

    public static Map<String, String> loadHashMap(File file) throws IOException {
        if (!file.exists()) {
            return new HashMap<>();
        }
        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
            return lines.filter(e -> e.contains(" ")).map(e -> e.split(" ")).collect(Collectors.toMap(e -> e[0], e-> e[1]));
        }
    }

    public static void saveHashMap(File file, Map<String, String> map) throws IOException {
        StringBuilder buf  = new StringBuilder();
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            buf.append(key).append(' ').append(map.get(key)).append('\n');
        }
        Files.write(file.toPath(), buf.toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }

}

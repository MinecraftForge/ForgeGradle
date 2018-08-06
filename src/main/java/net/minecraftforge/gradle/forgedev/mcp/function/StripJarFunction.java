package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.impldep.com.google.gson.JsonObject;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.internal.impldep.org.apache.commons.io.IOUtils;
import org.gradle.internal.impldep.org.apache.ivy.util.FileUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class StripJarFunction implements MCPFunction {

    private String mappings;
    private Set<String> filter;

    @Override
    public void loadData(JsonObject data) {
        mappings = data.get("mappings").getAsString();
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        // Read valid file names from mapping
        BufferedReader br = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(mappings))));
        filter = br.lines().filter(l -> !l.startsWith("\t")).map(s -> s.split(" ")[0] + ".class").collect(Collectors.toSet());
        br.close();
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = new File(environment.getArguments().get("input"));
        File inHashFile = environment.getFile("lastinput.sha1");
        File output = environment.getFile("output.jar");
        boolean whitelist = environment.getArguments().getOrDefault("mode", "whitelist").equalsIgnoreCase("whitelist");

        if (environment.shouldSkipStep()) return output;

        HashValue inputHash = HashUtil.sha1(input);

        // If this has already been computed, skip
        if (inHashFile.exists() && output.exists()) {
            HashValue cachedHash = HashValue.parse(FileUtil.readEntirely(inHashFile));
            if (inputHash.equals(cachedHash)) {
                return output;
            }
        }

        // Store the hash of the input for future reference
        if (inHashFile.exists()) inHashFile.delete();
        FileUtils.writeStringToFile(inHashFile, inputHash.asHexString());

        if (output.exists()) output.delete();
        strip(input, output, whitelist);

        return output;
    }

    private void strip(File input, File output, boolean whitelist) throws IOException {
        JarInputStream is = new JarInputStream(new FileInputStream(input));
        JarOutputStream os = new JarOutputStream(new FileOutputStream(output));

        // Ignore any entry that's not allowed
        JarEntry entry;
        while ((entry = is.getNextJarEntry()) != null) {
            if (!isEntryValid(entry, whitelist)) continue;
            os.putNextEntry(entry);
            IOUtils.copyLarge(is, os, 0, entry.getSize());
            os.closeEntry();
        }

        os.close();
        is.close();
    }

    private boolean isEntryValid(JarEntry entry, boolean whitelist) {
        if (entry.isDirectory()) return false;
        String name = entry.getName();
        return !name.startsWith("META-INF/") && filter.contains(name) == whitelist;
    }

}

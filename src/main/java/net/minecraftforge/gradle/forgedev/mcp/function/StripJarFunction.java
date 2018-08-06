package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.internal.impldep.org.apache.commons.io.IOUtils;
import org.gradle.internal.impldep.org.apache.ivy.util.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public class StripJarFunction implements MCPFunction {

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = new File(environment.getArguments().get("input"));
        File inHashFile = environment.getFile("lastinput.sha1");
        File output = environment.getFile("output.jar");

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
        strip(input, output);

        return output;
    }

    private void strip(File input, File output) throws IOException {
        JarInputStream is = new JarInputStream(new FileInputStream(input));
        JarOutputStream os = new JarOutputStream(new FileOutputStream(output));

        // Ignore any entry that's not allowed
        JarEntry entry;
        while ((entry = is.getNextJarEntry()) != null) {
            if (!isEntryValid(entry)) continue;
            os.putNextEntry(entry);
            IOUtils.copyLarge(is, os, 0, entry.getSize());
            os.closeEntry();
        }

        os.close();
        is.close();
    }

    private boolean isEntryValid(JarEntry entry) {
        if (entry.isDirectory()) return false;
        String name = entry.getName();
        return !name.startsWith("META-INF/")
                // Server
                && !name.startsWith("org/bouncycastle/") && !name.startsWith("org/bouncycastle/") && !name.startsWith("org/apache/")
                && !name.startsWith("com/google/") && !name.startsWith("com/mojang/authlib/") && !name.startsWith("com/mojang/util/")
                && !name.startsWith("gnu/trove/") && !name.startsWith("io/netty/") && !name.startsWith("javax/annotation/")
                && !name.startsWith("argo/") && !name.startsWith("it/unimi/dsi/fastutil/") && !name.startsWith("joptsimple/")
                // Client
                && !name.startsWith("assets/") && !name.startsWith("data/");
    }

}

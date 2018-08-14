package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InjectFunction implements MCPFunction {

    private String inject;
    private String template;
    private Map<String, byte[]> added;

    @Override
    public void loadData(JsonObject data) {
        inject = data.get("inject").getAsString();
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        added = zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(inject))
        .collect(Collectors.toMap(e -> e.getName().substring(inject.length()), e -> {
            try {
                return IOUtils.toByteArray(zip.getInputStream(e));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
        if (added.containsKey("package-info-template.java")) {
            template = new String(added.get("package-info-template.java"), StandardCharsets.UTF_8);
            added.remove("package-info-template.java");
        }
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = environment.getFile(environment.getArguments().get("input"));
        File inHashFile = environment.getFile("lastinput.sha1");
        File output = environment.getFile("output.jar");

        if (environment.shouldSkipStep()) return output; //TODO: Should move this to a helper function?

        Map<String, String> inputs = new HashMap<>();
        inputs.put("input", HashFunction.SHA1.hash(input));
        if (template != null) {
            inputs.put("template", HashFunction.SHA1.hash(template));
        }
        added.entrySet().forEach(e -> inputs.put(e.getKey(), HashFunction.SHA1.hash(e.getValue())));

        // If this has already been computed, skip
        if (inHashFile.exists() && output.exists()) {
            Map<String, String> cache = Utils.loadHashMap(inHashFile);
            if (cache.equals(inputs)) {
                return output;
            }
        }

        // Store the hash of the input for future reference
        if (inHashFile.exists()) inHashFile.delete();
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        output.createNewFile();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output)) ) {

            Set<String> visited = new HashSet<>();

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(entry);
                IOUtils.copyLarge(zis, zos);
                zos.closeEntry();
                if (template != null) {
                    String pkg = entry.isDirectory() && !entry.getName().endsWith("/") ? entry.getName() : entry.getName().indexOf('/') == -1 ? "" : entry.getName().substring(0, entry.getName().lastIndexOf('/'));
                    if (visited.add(pkg)) {
                        ZipEntry info = new ZipEntry(pkg + "/package-info.java");
                        info.setTime(0);
                        zos.putNextEntry(info);
                        zos.write(template.replace("{PACKAGE}", pkg.replaceAll("/", ".")).getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }

            for (Entry<String, byte[]> add : added.entrySet()) {
                ZipEntry info = new ZipEntry(add.getKey());
                info.setTime(0);
                zos.putNextEntry(info);
                zos.write(add.getValue());
                zos.closeEntry();
            }
            Utils.saveHashMap(inHashFile, inputs);
        }

        return output;
    }

    @Override
    public  void cleanup(MCPEnvironment environment) {
        if (this.added != null) {
            this.added.clear();
        }
    }

}

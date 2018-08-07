package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.gradle.internal.impldep.com.google.gson.JsonObject;
import org.gradle.internal.impldep.org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class InjectFunction implements MCPFunction {

    private String inject;

    @Override
    public void loadData(JsonObject data) {
        inject = data.get("inject").getAsString();
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        Utils.extractDirectory(environment::getFile, zip, inject);
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = environment.getFile(environment.getArguments().get("input"));
        File output = environment.getFile("output.jar");
        File injectDir = environment.getFile(inject);

        ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output));

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            zos.putNextEntry(entry);
            IOUtils.copyLarge(zis, zos, 0, entry.getSize());
            zos.closeEntry();
        }

        int firstCharacter = injectDir.getAbsolutePath().length() + 1;
        Set<File> visited = new HashSet<>();
        Queue<File> toVisit = new ArrayDeque<>();
        toVisit.add(injectDir);
        while (!toVisit.isEmpty()) {
            visit(firstCharacter, toVisit.poll(), zos, visited, toVisit);
        }

        zos.close();
        zis.close();

        return output;
    }

    private void visit(int firstCharacter, File dir, ZipOutputStream zos, Set<File> visited, Queue<File> toVisit) throws IOException {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (visited.add(f)) toVisit.add(f);
            } else {
                zos.putNextEntry(new ZipEntry(f.getAbsolutePath().substring(firstCharacter)));
                InputStream is = new FileInputStream(f);
                IOUtils.copy(is, zos);
                is.close();
            }
        }
    }

}

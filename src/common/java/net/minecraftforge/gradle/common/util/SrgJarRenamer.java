package net.minecraftforge.gradle.common.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

public class SrgJarRenamer {

    public static boolean rename(File input, File output, File names) throws IOException {
        if (input == null || !input.exists())
            throw new IllegalArgumentException("Invalid input: " + input);

        if (output == null)
            output = input;

        if (names == null || !names.exists())
            throw new IllegalArgumentException("Invalid names file: " + names);

        McpNames map = McpNames.load(names);

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        ByteArrayOutputStream memory = input.equals(output) ? new ByteArrayOutputStream() : null;
        try (ZipOutputStream zout = new ZipOutputStream(memory == null ? new FileOutputStream(output) : memory);
            ZipInputStream zin = new ZipInputStream(new FileInputStream(input))) {
            ZipEntry ein = null;
            while ((ein = zin.getNextEntry()) != null) {
                if (ein.getName().endsWith(".class")) {
                    byte[] data = rename(IOUtils.toByteArray(zin), map);
                    ZipEntry eout = new ZipEntry(ein.getName());
                    eout.setTime(0); //Cant copy time cuz it explodes... unknown why...
                    zout.putNextEntry(eout);
                    zout.write(data);
                } else {
                    zout.putNextEntry(ein);
                    IOUtils.copy(zin, zout);
                }
            }
        }

        if (memory != null)
            Files.write(output.toPath(), memory.toByteArray());

        return true;
    }

    private static byte[] rename(byte[] data, final McpNames map) {
        ClassReader reader = new ClassReader(data);
        ClassWriter writer = new ClassWriter(0);
        ClassRemapper remapper = new ClassRemapper(writer, new Remapper() {
            @Override
            public String mapFieldName(final String owner, final String name, final String descriptor) {
                return map.rename(name);
            }
            @Override
            public String mapInvokeDynamicMethodName(final String name, final String descriptor) {
                return map.rename(name);
            }
            @Override
            public String mapMethodName(final String owner, final String name, final String descriptor) {
              return map.rename(name);
            }
        });
        reader.accept(remapper, 0);

        return writer.toByteArray();
    }

}

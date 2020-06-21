package net.minecraftforge.gradle.common.diff;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PatchFile {

    public static PatchFile from(String name, String string) {
        return new PatchFile(name, () -> new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)), false);
    }

    public static PatchFile from(String name, byte[] data) {
        return new PatchFile(name, () -> new ByteArrayInputStream(data), false);
    }

    public static PatchFile from(String name, File file) {
        return new PatchFile(name, () -> new FileInputStream(file), true);
    }

    private final String name;
    private final PatchSupplier supplier;
    private final boolean requiresFurtherProcessing;

    private PatchFile(String name, PatchSupplier supplier, boolean requiresFurtherProcessing) {
        this.name = name;
        this.supplier = supplier;
        this.requiresFurtherProcessing = requiresFurtherProcessing;
    }

    String getName() {
        return name;
    }

    InputStream openStream() throws IOException {
        return supplier.get();
    }

    boolean requiresFurtherProcessing() {
        return requiresFurtherProcessing;
    }

    interface PatchSupplier {
        InputStream get() throws IOException;
    }

}

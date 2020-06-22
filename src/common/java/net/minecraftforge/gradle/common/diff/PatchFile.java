package net.minecraftforge.gradle.common.diff;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PatchFile {

    public static PatchFile from(String string) {
        return new PatchFile(() -> new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)), false);
    }

    public static PatchFile from(byte[] data) {
        return new PatchFile(() -> new ByteArrayInputStream(data), false);
    }

    public static PatchFile from(File file) {
        return new PatchFile(() -> new FileInputStream(file), true);
    }

    private final PatchSupplier supplier;
    private final boolean requiresFurtherProcessing;

    private PatchFile(PatchSupplier supplier, boolean requiresFurtherProcessing) {
        this.supplier = supplier;
        this.requiresFurtherProcessing = requiresFurtherProcessing;
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

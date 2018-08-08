package net.minecraftforge.gradle.common.diff;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PatchFile {

    static PatchFile fromString(String string) {
        return new PatchFile(() -> new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8)), false);
    }

    static PatchFile fromFile(File file) {
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

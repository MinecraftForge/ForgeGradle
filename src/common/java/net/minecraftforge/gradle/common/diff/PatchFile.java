package net.minecraftforge.gradle.common.diff;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public interface PatchFile {

    static PatchFile fromString(String string) {
        return () -> new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    static PatchFile fromFile(File file) {
        return () -> new FileInputStream(file);
    }

    InputStream openStream() throws IOException;

}

package net.minecraftforge.gradle.common.mapping.generator;

import java.io.BufferedWriter;
import java.io.FilterWriter;
import java.io.IOException;

class UnclosingWriter extends FilterWriter {

    UnclosingWriter(BufferedWriter out) {
        super(out);
    }

    @Override
    public void close() throws IOException {
        super.flush();
    }
}
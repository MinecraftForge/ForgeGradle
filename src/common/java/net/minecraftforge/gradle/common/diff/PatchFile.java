/*
 * ForgeGradle
 * Copyright (C) 2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

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

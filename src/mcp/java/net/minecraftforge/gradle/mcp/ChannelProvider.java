/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
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

package net.minecraftforge.gradle.mcp;

import com.google.common.collect.ImmutableSet;
import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.LineDelimiter;
import net.minecraftforge.gradle.common.util.Utils;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipOutputStream;

public abstract class ChannelProvider {
    private final Set<String> channels;

    protected ChannelProvider(String... channels) {
        this.channels = ImmutableSet.copyOf(channels);
    }

    protected ChannelProvider(Set<String> channels) {
        this.channels = ImmutableSet.copyOf(channels);
    }

    public Set<String> getChannels() {
        return channels;
    }

    @Nullable
    public abstract File getMappingsFile(MCPRepo mcpRepo, Project project, String channel, String version) throws IOException;

    protected void writeCsv(String name, List<String[]> mappings, ZipOutputStream out) throws IOException {
        if (mappings.size() <= 1)
            return;
        out.putNextEntry(Utils.getStableEntry(name));
        try (CsvWriter writer = CsvWriter.builder().lineDelimiter(LineDelimiter.LF).build(new UncloseableOutputStreamWriter(out))) {
            mappings.forEach(writer::writeRow);
        }
        out.closeEntry();
    }

    private static class UncloseableOutputStreamWriter extends OutputStreamWriter {
        private UncloseableOutputStreamWriter(OutputStream out) {
            super(out);
        }

        @Override
        public void close() throws IOException {
            super.flush();
        }
    }
}

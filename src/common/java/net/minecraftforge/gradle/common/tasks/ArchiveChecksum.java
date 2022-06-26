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

package net.minecraftforge.gradle.common.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// TODO: check for uses
public abstract class ArchiveChecksum extends DefaultTask {
    //TODO: Filters of some kind?

    public ArchiveChecksum() {
        getOutput().convention(getProjectLayout().getBuildDirectory().dir(getName()).map(s -> s.file("output.sha256")));
    }

    @TaskAction
    public void run() throws IOException {
        Map<String, String> checksums = Maps.newTreeMap(); //Tree so we're sorted alphabetically!
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(getInput().get().getAsFile()))) {
            ZipEntry entry;
            byte[] buff = new byte[1024];
            while ((entry = zin.getNextEntry()) != null) {
                @SuppressWarnings("resource") //We dont want to close the input
                HashingInputStream hash = new HashingInputStream(Hashing.sha256(), zin);
                while (hash.read(buff, 0, buff.length) != -1); //Read all the data, we dont care, but the stream auto-hashes;
                checksums.put(entry.getName(), Base64.getEncoder().encodeToString(hash.hash().asBytes()));
            }
        }

        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getOutput().get().getAsFile())))) {
            checksums.forEach((name, hash) -> {
                out.write(hash);
                out.write(' ');
                out.write(name);
                out.write('\n');
            });
        }
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

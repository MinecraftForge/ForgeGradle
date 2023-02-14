/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;
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
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(s -> s.file("output.sha256")));
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

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectories;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public abstract class ExtractExistingFiles extends DefaultTask {
    @TaskAction
    public void run() throws IOException {
        try (ZipFile zip = new ZipFile(getArchive().get().getAsFile())) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry e = enu.nextElement();
                if (e.isDirectory()) continue;

                for (File target : getTargets()) {
                    File out = new File(target, e.getName());
                    if (!out.exists()) continue;
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        IOUtils.copy(zip.getInputStream(e), fos);
                    }
                }
            }
        }
    }

    @InputFile
    public abstract RegularFileProperty getArchive();

    @OutputDirectories
    public abstract ConfigurableFileCollection getTargets();
}

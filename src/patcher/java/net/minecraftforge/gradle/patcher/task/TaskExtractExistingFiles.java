package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

public class TaskExtractExistingFiles extends DefaultTask {
    private File archive;
    private List<File> targets = new ArrayList<>();

    @TaskAction
    public void run() throws IOException {
        try (ZipFile zip = new ZipFile(getArchive())) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry e = enu.nextElement();
                if (e.isDirectory()) continue;

                for (File target : targets) {
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
    public File getArchive() {
        return this.archive;
    }
    public void setArchive(File value) {
        this.archive = value;
    }

    public void addTarget(File value) {
        this.targets.add(value);
    }
}

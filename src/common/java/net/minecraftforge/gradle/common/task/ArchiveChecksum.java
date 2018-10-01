package net.minecraftforge.gradle.common.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashingInputStream;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ArchiveChecksum extends DefaultTask {

    private Supplier<File> input;
    private File output;
    //TODO: Filters of some kind?

    @Input
    public File getInput() {
        return input.get();
    }
    public void setInput(File value) {
        this.input = () -> value;
    }
    public void setInput(Supplier<File> value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            output = getProject().file("build/" + getName() + "/output.sha256");
        return output;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    public void setName(String value) {
        if (output == null)
            setOutput(getProject().file("build/" + getName() + "/" + value + ".sha256"));
    }

    @TaskAction
    public void run() throws IOException {
        Map<String, String> checksums = Maps.newTreeMap(); //Tree so we're sorted alphabetically!
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(getInput()))) {
            ZipEntry entry;
            byte[] buff = new byte[1024];
            while ((entry = zin.getNextEntry()) != null) {
                @SuppressWarnings("resource") //We dont want to close the input
                HashingInputStream hash = new HashingInputStream(Hashing.sha256(), zin);
                while (hash.read(buff, 0, buff.length) != -1); //Read all the data, we dont care, but the stream auto-hashes;
                checksums.put(entry.getName(), Base64.getEncoder().encodeToString(hash.hash().asBytes()));
            }
        }

        try(PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(getOutput())))) {
            checksums.forEach((name, hash) -> {
                out.write(hash);
                out.write(' ');
                out.write(name);
                out.write('\n');
            });
        }
    }
}

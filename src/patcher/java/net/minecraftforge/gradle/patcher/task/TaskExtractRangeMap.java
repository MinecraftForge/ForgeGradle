package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.srg2source.ast.RangeExtractor;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaskExtractRangeMap extends DefaultTask {

    private Set<File> sources;
    private Set<File> dependencies = new HashSet<>();
    private File output = getProject().file("build/" + getName() + "/output.txt");
    private File log = getProject().file("build/" + getName() + "/log.txt");

    @TaskAction
    public void extractRangeMap() throws IOException {
        RangeExtractor extract = new RangeExtractor(RangeExtractor.JAVA_1_8);
        getDependencies().forEach(extract::addLibs);
        if (getOutput().exists()) {
            try (InputStream fin = new FileInputStream(getOutput())) {
                extract.loadCache(fin);
            }
        }
        extract.setSrc(getInputSupplier());
        try (FileOutputStream fos = new FileOutputStream(log)) {
            extract.setOutLogger(new PrintStream(fos));
            if (!extract.generateRangeMap(getOutput())) {
                throw new RuntimeException("RangeExtractor failed, Check Log for details: " + log);
            }
        }
    }

    private InputSupplier getInputSupplier() throws IOException {
        final List<InputSupplier> inputs = new ArrayList<>();
        for (File src : getSources()) {
            if (src.exists()) {
                inputs.add(src.isDirectory() ? new FolderSupplier(src) : new ZipInputSupplier(src));
            }
        }

        if (inputs.size() == 1) {
            return inputs.get(0);
        } else {
            return new InputSupplier() {
                @Override
                public void close() throws IOException {
                    for (InputSupplier sup : inputs) {
                        sup.close();
                    }
                }

                @Override
                public String getRoot(String resource) {
                    return inputs.stream().map(sup -> sup.getRoot(resource)).filter(v -> v != null).findFirst().orElse(null);
                }

                @Override
                public InputStream getInput(String resource) {
                    return inputs.stream().map(sup -> sup.getInput(resource)).filter(v -> v != null).findFirst().orElse(null);
                }

                @Override
                public List<String> gatherAll(String path) {
                    List<String> ret = new ArrayList<>();
                    inputs.forEach(s -> ret.addAll(s.gatherAll(path)));
                    return ret;
                }
            };
        }
    }

    @InputFiles
    public Set<File> getSources() {
        return sources;
    }

    @InputFiles
    public Set<File> getDependencies() {
        return dependencies;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setSources(Set<File> sources) {
        this.sources = sources;
    }

    public void addDependencies(Collection<File> dependencies) {
        this.dependencies.addAll(dependencies);
    }

    public void addDependencies(File... dependencies) {
        for (File dep : dependencies) {
            this.dependencies.add(dep);
        }
    }

    public void setOutput(File output) {
        this.output = output;
    }

}

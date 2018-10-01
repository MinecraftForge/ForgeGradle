package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class TaskGenerateBinPatches extends DefaultTask {

    private String tool = "net.minecraftforge:binarypatcher:1.+:fatjar";
    private File _tool;
    private String[] args = new String[] { "--clean", "{clean}", "--create", "{dirty}", "--output", "{output}", "--patches", "{patches}", "--srg", "{srg}"};
    private FileCollection classpath = null;

    private File cleanJar;
    private File dirtyJar;
    private File srg;
    private Set<File> patchSets = new HashSet<>();
    private String side;
    private File output = null;

    @TaskAction
    public void apply() throws IOException {

        File jar = getToolJar();

        Map<String, String> replace = new HashMap<>();
        replace.put("{clean}", getCleanJar().getAbsolutePath());
        replace.put("{dirty}", getDirtyJar().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        replace.put("{srg}", getSrg().getAbsolutePath());

        List<String> _args = new ArrayList<>();
        for (String arg : args) {
            if ("{patches}".equals(arg)) {
                String prefix = _args.get(_args.size() - 1);
                _args.remove(_args.size() - 1);
                getPatchSets().forEach(f -> {
                   _args.add(prefix);
                   _args.add(f.getAbsolutePath());
                });
            } else {
                _args.add(replace.getOrDefault(arg, arg));
            }
        }

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        File workDir = getProject().file("build/" + getName());
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        try (OutputStream log = new BufferedOutputStream(new FileOutputStream(new File(workDir, "log.txt")))) {
            // Execute command
            JavaExec java = getProject().getTasks().create("_", JavaExec.class);
            java.setArgs(_args);
            if (getClasspath() == null)
                java.setClasspath(getProject().files(jar));
            else
                java.setClasspath(getProject().files(jar, getClasspath()));
            java.setWorkingDir(workDir);
            java.setMain(mainClass);
            java.setStandardOutput(new OutputStream() {
                @Override
                public void flush() throws IOException {
                    log.flush();
                }
                @Override
                public void close() {}
                @Override
                public void write(int b) throws IOException {
                    log.write(b);
                }
            });
            java.exec();
            getProject().getTasks().remove(java);
        }
    }

    public String getResolvedVersion() {
        return MavenArtifactDownloader.getVersion(getProject(), getTool());
    }

    @InputFile
    public File getToolJar() {
        if (_tool == null)
            _tool = MavenArtifactDownloader.single(getProject(), getTool());
        return _tool;
    }

    @Input
    public String getTool() {
        return tool;
    }

    public void setTool(String value) {
        this.tool = value;
    }
    @Input
    public String[] getArgs() {
        return this.args;
    }
    public void setArgs(String[] value) {
        this.args = value;
    }
    @Optional
    @InputFiles
    public FileCollection getClasspath() {
        return this.classpath;
    }
    public void setClasspath(FileCollection value) {
        this.classpath = value;
    }

    @InputFile
    public File getCleanJar() {
        return cleanJar;
    }
    public void setCleanJar(File value) {
        this.cleanJar = value;
    }

    @InputFile
    public File getDirtyJar() {
        return dirtyJar;
    }
    public void setDirtyJar(File value) {
        this.dirtyJar = value;
    }

    @InputFiles
    public Set<File> getPatchSets() {
        return this.patchSets;
    }
    public void addPatchSet(File value) {
        if (value != null) {
            this.patchSets.add(value);
        }
    }

    @InputFile
    public File getSrg() {
        return this.srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Input
    public String getSide() {
        return this.side;
    }
    public void setSide(String value) {
        this.side = value;
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/" + getSide() + ".lzma"));
        }
    }

    @OutputFile
    public File getOutput() {
        if (output == null) {
            setOutput(getProject().file("build/" + getName() + "/output.lzma"));
        }
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

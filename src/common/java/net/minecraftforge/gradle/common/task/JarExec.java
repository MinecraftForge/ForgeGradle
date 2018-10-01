package net.minecraftforge.gradle.common.task;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;

public class JarExec extends DefaultTask {

    protected String tool;
    private File _tool;
    protected String[] args;
    protected FileCollection classpath = null;

    @TaskAction
    public void apply() throws IOException {

        File jar = getToolJar();

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        File workDir = getProject().file("build/" + getName());
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        File logFile = new File(workDir, "log.txt");

        try (OutputStream log = new BufferedOutputStream(new FileOutputStream(logFile))) {
            // Execute command
            JavaExec java = getProject().getTasks().create("_", JavaExec.class);
            java.setArgs(filterArgs());
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

        postProcess(logFile);
    }

    protected List<String> filterArgs() {
        return Arrays.asList(getArgs());
    }

    protected void postProcess(File log) {
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
}

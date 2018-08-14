package net.minecraftforge.gradle.patcher.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class DownloadMCPMappingsTask extends DefaultTask {

    private Object mappings;

    private File output = getProject().file("build/mappings.zip");

    @Input
    public Object getMappings() {
        return this.mappings;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setMappings(Object value) {
        this.mappings = value;
    }

    public void setOutput(File output) {
        this.output = output;
    }

    @TaskAction
    public void download() throws IOException {
        File out = getMappingFile();
        if (out != null && out.exists()) {
            this.setDidWork(true);
        } else {
            this.setDidWork(false);
        }
        if (FileUtils.contentEquals(out, output)) return;
        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        FileUtils.copyFile(out, output);
    }

    private File getMappingFile() {
        if (getMappings() instanceof String) {
            String artifact = (String) getMappings();
            return MavenArtifactDownloader.download(getProject(), artifact).iterator().next();
        } else if (getMappings() instanceof File) {
            return (File) getMappings();
        } else {
            throw new IllegalArgumentException("Expected the mappings to be a File or a String, but instead got " + (getMappings() == null ? "null" : getMappings().getClass().getName()));
        }
    }

}

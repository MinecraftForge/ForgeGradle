package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMCPConfigTask extends DefaultTask {

    private String config;
    private File output;

    @TaskAction
    public void downloadMCPConfig() throws IOException {
        File file = getConfigFile();

        if (getOutput().exists()) {
            if (FileUtils.contentEquals(file, getOutput())) {
                // NO-OP: The contents of both files are the same, we're up to date
                setDidWork(false);
                return;
            } else {
                getOutput().delete();
            }
        }
        FileUtils.copyFile(file, getOutput());
        setDidWork(true);
    }

    public Object getConfig() {
        return this.config;
    }

    @InputFile
    private File getConfigFile() {
        return downloadConfigFile(config);
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setConfig(String value) {
        this.config = value;
    }

    public void setOutput(File value) {
        this.output = value;
    }

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.download(getProject(), config).iterator().next();
    }

}

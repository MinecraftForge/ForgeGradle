package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.apache.commons.io.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;

public class DownloadMCPConfigTask extends DefaultTask {

    private Object config;
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

    @Input
    public Object getConfig() {
        return this.config;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }

    public void setConfig(Object value) {
        this.config = value;
    }

    public void setOutput(File value) {
        this.output = value;
    }

    private File getConfigFile() {
        if (config instanceof String) {
            if (((String) config).contains(":")) {
                return downloadConfigFile((String) config);
            } else {
                return new File((String) config);
            }
        } else if (config instanceof File) {
            return (File) config;
        } else {
            throw new IllegalArgumentException("Expected the config to be a File or a String, but instead got " + config.getClass().getName());
        }
    }

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.download(getProject(), config).iterator().next();
    }

}

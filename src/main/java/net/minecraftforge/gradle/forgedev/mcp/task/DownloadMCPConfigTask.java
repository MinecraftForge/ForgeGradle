package net.minecraftforge.gradle.forgedev.mcp.task;

import net.minecraftforge.gradle.forgedev.mcp.util.MavenArtifactDownloader;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

public class DownloadMCPConfigTask extends DefaultTask {

    @Input
    public Object config;
    @OutputFile
    public File output = new File("build/mcp/mcp_config.zip"); // TODO: Do this the right way

    @TaskAction
    public void downloadMCPConfig() throws IOException {
        File file = getConfigFile();

        if (output.exists()) {
            if (FileUtils.contentEquals(file, output)) {
                // NO-OP: The contents of both files are the same, we're up to date
                setDidWork(false);
                return;
            } else {
                output.delete();
            }
        }
        FileUtils.copyFile(file, output);
        setDidWork(true);
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

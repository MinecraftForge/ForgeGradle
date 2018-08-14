package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;

import com.google.common.base.Suppliers;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

public class DownloadMCPConfigTask extends DefaultTask {

    private Object _config;
    @Input public Object getConfig() { return this._config; }
    public void setConfig(Object value) { this._config = value; }

    private Supplier<File> _output = Suppliers.memoize(() -> getProject().file("build/mcp_config.zip"));
    @OutputFile public File getOutput() { return _output.get(); }
    public Supplier<File> getOutputLazy() { return _output; }
    public void setOutput(File value) { this._output = () -> value; }
    public void setOutput(Supplier<File> value) { this._output = Suppliers.memoize(value::get); } //SUPER ugly because we're not using groovy, but whatever...

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

    private File getConfigFile() {
        if (getConfig() instanceof String) {
            if (((String) getConfig()).contains(":")) {
                return downloadConfigFile((String) getConfig());
            } else {
                return new File((String) getConfig());
            }
        } else if (getConfig() instanceof File) {
            return (File) getConfig();
        } else {
            throw new IllegalArgumentException("Expected the config to be a File or a String, but instead got " + getConfig().getClass().getName());
        }
    }

    private File downloadConfigFile(String config) {
        return MavenArtifactDownloader.download(getProject(), config).iterator().next();
    }

}

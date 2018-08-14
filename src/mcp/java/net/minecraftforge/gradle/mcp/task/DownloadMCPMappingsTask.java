package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Suppliers;

import groovy.lang.Closure;

import java.io.File;
import java.util.function.Supplier;

public class DownloadMCPMappingsTask extends DefaultTask {

    private Supplier<String> _version;
    @Input public String getVersion() { return this._version.get(); }
    public void setVersion(Supplier<String> value) { this._version = Suppliers.memoize(value::get); }
    public void setVersion(String value) { this._version = () -> value; }

    private Supplier<Object> _mappings;
    @Input public Object getMappings() { return this._mappings.get(); }
    public void setMappings(Supplier<Object> value) { this._mappings = Suppliers.memoize(value::get); }
    public void setMappings(Object value) { this._mappings = () -> value; }

    private Supplier<File> _output;
    public File getOutput() { return this._output.get(); }
    public Supplier<File> getOutputLazy() { return this._output; }
    public void setOutput(Supplier<File> value) { this._output = Suppliers.memoize(value::get); }
    public void setOutput(File value) { this._output = () -> value; }

    @TaskAction
    public void download() {
        File output = getMappingFile();
        if (output != null && output.exists()) {
            this.setDidWork(true);
        } else {
            this.setDidWork(false);
        }
    }

    public static Object getDefault(String channel, String version) {
        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify mappings channel and version");
        }
        return "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
    }

    private File getMappingFile() {
        if (getMappings() instanceof String) {
            String artifact = (String)getMappings();
            String[] pts = artifact.split(":");
            if (artifact.startsWith("de.oceanlabs.mcp:mcp_") && pts[2].indexOf('-') == -1) { //Default artifact, but without MC version, so lets add it.
                String ext = "";
                int at = pts[2].indexOf('@');
                if (at != -1) {
                    ext = pts[2].substring(at);
                    pts[2] = pts[2].substring(0, at-1);
                }
                pts[2] += "-" + getVersion() + ext;
                artifact = String.join(":", pts);
            }
            return MavenArtifactDownloader.download(getProject(), artifact).iterator().next();
        } else if (getMappings() instanceof File) {
            return (File)getMappings();
        } else {
            throw new IllegalArgumentException("Expected the mappings to be a File or a String, but instead got " + (getMappings() == null ? "null" : getMappings().getClass().getName()));
        }
    }
}

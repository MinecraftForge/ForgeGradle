package net.minecraftforge.gradle.mcp.task;

import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

public class SetupMCPTask extends DefaultTask {

    private String _skip = "";
    @Input public String getSkipped() { return _skip; }

    private List<Supplier<String>> _ats = new ArrayList<>();
    private List<String> _ats_compiled = null;
    @Input public List<String> getAccessTransformers() {
        if (_ats_compiled == null) {
            _ats_compiled = _ats.stream().map(e -> e.get()).collect(Collectors.toList());
        }
        return _ats_compiled;
    }
    public void addAccessTransformer(Supplier<String> value) {
        _ats.add(value);
        _ats_compiled = null;
    }
    public void addAccessTranformer(String value) { addAccessTransformer(() -> value); }

    private File _output;
    public Supplier<File> getOutput() { return () -> _output; } //Supplier only valid after this task has run...

    @Input //Doesnt work on Fields...
    public MCPConfig config;

    @TaskAction
    public void setupMCP() throws Exception {
        getLogger().info("Setting up MCP!");
        MCPRuntime runtime = new MCPRuntime(getProject(), config, true);
        _output = runtime.execute(getLogger(), getSkipped().split(","));
    }

    //Is there any real world use for Skip, besides FG dev testing?
    @Option(option = "skip", description = "Comma-separated list of tasks to be skipped")
    public void setSkipped(String skip) {
        this._skip = skip;
    }
}

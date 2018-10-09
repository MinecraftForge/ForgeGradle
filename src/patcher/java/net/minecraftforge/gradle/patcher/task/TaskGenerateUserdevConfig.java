package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.patcher.PatcherExtension;

public class TaskGenerateUserdevConfig extends DefaultTask {
    private Set<File> ats = new TreeSet<>();
    private Set<File> srgs = new TreeSet<>();
    private List<String> srgLines = new ArrayList<>();
    private File output = getProject().file("build/" + getName() + "/output.json");
    private boolean srg = false;

    @TaskAction
    public void apply() throws IOException {
        UserdevConfigV1 json = new UserdevConfigV1(); //TODO: Move this to plugin so we can re-use the names in both tasks?
        json.spec = 1;
        json.binpatches = "joined.lzma";
        json.sources = "sources.jar";
        json.universal = "universal.jar";
        json.patches = "patches/";
        json.srg = srg;
        getATs().forEach(at -> json.addAT("ats/" + at.getName()));
        getSRGs().forEach(srg -> json.addSRG("srgs/" + srg.getName()));
        getSRGLines().forEach(srg -> json.addSRGLine(srg));
        addParent(json, getProject());

        Files.write(Utils.GSON.toJson(json).getBytes(StandardCharsets.UTF_8), getOutput());
    }

    private void addParent(UserdevConfigV1 json, Project project) {
        PatcherExtension patcher = project.getExtensions().findByType(PatcherExtension.class);
        MCPExtension mcp = project.getExtensions().findByType(MCPExtension.class);

        if (patcher != null) {
            if (project != getProject() && patcher.patches != null) { //patches == null means they dont add anything, used by us as a 'clean' workspace.
                if (json.parent == null) {
                    json.parent = String.format("%s:%s:%s:userdev", project.getGroup(), project.getName(), project.getVersion());
                    return;
                }
            }
            if (patcher.parent != null) {
                addParent(json, patcher.parent);
            }
            //TODO: MCP/Parents without separate projects?
        }
        if (json.parent != null && mcp != null) { //Only specify mcp if we have no patcher parent.
            json.mcp = mcp.getConfig().toString();;
        }
    }

    @Input
    public Set<File> getATs() {
        return this.ats;
    }
    public void addAT(File value) {
        this.ats.add(value);
    }

    @Input
    public Set<File> getSRGs() {
        return this.srgs;
    }
    public void addSRG(File value) {
        this.srgs.add(value);
    }

    @Input
    public List<String> getSRGLines() {
        return this.srgLines;
    }
    public void addSRGLine(String value) {
        this.srgLines.add(value);
    }

    @Input
    public boolean getSRG() {
        return srg;
    }
    public void setSRG(boolean value) {
        this.srg = value;
    }

    @OutputFile
    public File getOutput() {
        return this.output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

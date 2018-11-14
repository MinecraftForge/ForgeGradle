/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.patcher.task;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.patcher.PatcherExtension;

public class TaskGenerateUserdevConfig extends DefaultTask {
    private Set<File> ats = new TreeSet<>();
    private Set<File> srgs = new TreeSet<>();
    private List<String> srgLines = new ArrayList<>();
    private File output = getProject().file("build/" + getName() + "/output.json");
    private String universal;
    private String source;
    private String tool;
    private String[] args;
    private List<String> libraries;
    private String inject;

    @TaskAction
    public void apply() throws IOException {
        UserdevConfigV1 json = new UserdevConfigV1(); //TODO: Move this to plugin so we can re-use the names in both tasks?
        json.spec = 1;
        json.binpatches = "joined.lzma";
        json.sources = source;
        json.universal = universal;
        json.patches = "patches/";
        json.inject = "inject/";
        if (libraries != null && !libraries.isEmpty())
            libraries.forEach(json::addLibrary);
        getATs().forEach(at -> json.addAT("ats/" + at.getName()));
        getSRGs().forEach(srg -> json.addSRG("srgs/" + srg.getName()));
        getSRGLines().forEach(srg -> json.addSRGLine(srg));
        addParent(json, getProject());

        json.binpatcher = new Function();
        json.binpatcher.setVersion(getTool());
        json.binpatcher.setArgs(Arrays.asList(args));

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
        } else {
            if (json.parent == null) { //Only specify mcp if we have no patcher parent.
                if (mcp == null)
                    throw new IllegalStateException("Could not determine MCP parent for userdev config");
                json.mcp = mcp.getConfig().toString();;
            }
        }
    }

    @Input
    public List<String> getLibraries() {
        return libraries == null ? Collections.emptyList() : libraries;
    }
    public void setLibrary(String value) {
        if (libraries == null)
            libraries = new ArrayList<>();
        libraries.add(value);
    }
    public void addLibrary(String value) {
        setLibrary(value);
    }

    @Input
    public String getUniversal() {
        return universal;
    }
    public void setUniversal(String value) {
        this.universal = value;
    }

    @Input
    public String getSource() {
        return source;
    }
    public void setSource(String value) {
        this.source = value;
    }

    @Input
    public String getTool() {
        return tool;
    }
    public void setTool(String value) {
        this.tool = value;
    }

    @Input
    @Optional
    public String getInject() {
        return inject;
    }
    public void setInject(String value) {
        this.inject = value;
    }

    @Input
    public String[] getArguments() {
        return args == null ? new String[0] : args;
    }
    public void setArguments(String... value) {
        this.args = value;
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

    @OutputFile
    public File getOutput() {
        return this.output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

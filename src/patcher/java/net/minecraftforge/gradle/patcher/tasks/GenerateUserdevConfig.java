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

package net.minecraftforge.gradle.patcher.tasks;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.config.UserdevConfigV2;
import net.minecraftforge.gradle.common.config.UserdevConfigV2.DataFunction;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.patcher.PatcherExtension;

import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;

public abstract class GenerateUserdevConfig extends DefaultTask {

    private final NamedDomainObjectContainer<RunConfig> runs;

    @Nullable
    private DataFunction processor;
    private MapProperty<String, File> processorData;

    private boolean notchObf = false;

    @Inject
    public GenerateUserdevConfig(@Nonnull final Project project) {
        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));

        ObjectFactory objects = project.getObjects();
        getPatchesOriginalPrefix().convention("a/");
        getPatchesModifiedPrefix().convention("b/");
        getSourceFileEncoding().convention(StandardCharsets.UTF_8.name());

        processorData = objects.mapProperty(String.class, File.class);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.json")));
    }

    @TaskAction
    public void apply() throws IOException {
        UserdevConfigV2 json = new UserdevConfigV2(); //TODO: Move this to plugin so we can re-use the names in both tasks?
        json.spec = isV2() ? 2 : 1;
        json.binpatches = "joined.lzma";
        json.sources = getSource().get();
        json.universal = getUniversal().get();
        json.patches = "patches/";
        json.inject = "inject/";
        getLibraries().get().forEach(json::addLibrary);
        getATs().forEach(at -> json.addAT("ats/" + at.getName()));
        getSASs().forEach(at -> json.addSAS("sas/" + at.getName()));
        getSRGs().forEach(srg -> json.addSRG("srgs/" + srg.getName()));
        getSRGLines().get().forEach(json::addSRG);
        addParent(json, getProject());

        runs.getAsMap().forEach(json::addRun);

        json.binpatcher = new Function();
        json.binpatcher.setVersion(getTool().get());
        json.binpatcher.setArgs(getArguments().get());

        if (isV2()) {
            json.processor = processor;
            json.patchesOriginalPrefix = getPatchesOriginalPrefix().get();
            json.patchesModifiedPrefix = getPatchesModifiedPrefix().get();
            json.setNotchObf(notchObf);
            json.setSourceFileCharset(getSourceFileEncoding().get());
            getUniversalFilters().get().forEach(json::addUniversalFilter);
        }

        Files.write(Utils.GSON.toJson(json).getBytes(StandardCharsets.UTF_8), getOutput().get().getAsFile());
    }

    private void addParent(UserdevConfigV1 json, Project project) {
        PatcherExtension patcher = project.getExtensions().findByType(PatcherExtension.class);
        MCPExtension mcp = project.getExtensions().findByType(MCPExtension.class);

        if (patcher != null) {
            if (project != getProject() && patcher.getPatches() == null) { //patches == null means they dont add anything, used by us as a 'clean' workspace.
                if (json.parent == null) {
                    json.parent = String.format("%s:%s:%s:userdev", project.getGroup(), project.getName(), project.getVersion());
                    return;
                }
            }
            if (patcher.getParent() != null) {
                addParent(json, patcher.getParent());
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

    private boolean isV2() {
        return this.notchObf || this.processor != null || this.getUniversalFilters().isPresent() ||
                !"a/".equals(getPatchesOriginalPrefix().get()) ||
                !"b/".equals(getPatchesModifiedPrefix().get());
    }

    @Input
    public abstract ListProperty<String> getLibraries();

    @Input
    public abstract Property<String> getUniversal();

    @Input
    public abstract Property<String> getSource();

    @Input
    public abstract Property<String> getTool();

    @Input
    @Optional
    public abstract Property<String> getInject();

    @Input
    public abstract ListProperty<String> getArguments();

    @InputFiles
    public abstract ConfigurableFileCollection getATs();

    @InputFiles
    public abstract ConfigurableFileCollection getSASs();

    @InputFiles
    public abstract ConfigurableFileCollection getSRGs();

    @Input
    @Optional
    public abstract ListProperty<String> getSRGLines();

    public NamedDomainObjectContainer<RunConfig> runs(@SuppressWarnings("rawtypes") Closure closure) {
        return runs.configure(closure);
    }

    @Input
    public abstract NamedDomainObjectContainer<RunConfig> getRuns();

    public void propertyMissing(String name, Object value) {
        if (!(value instanceof Closure)) {
            throw new MissingPropertyException(name);
        }

        @SuppressWarnings("rawtypes")
        final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    private DataFunction ensureProcessor() {
        if (this.processor == null)
            this.processor = new DataFunction();
        return this.processor;
    }

    public void setProcessor(DataFunction value) {
        ensureProcessor();
        this.processor.setVersion(value.getVersion());
        this.processor.setRepo(value.getRepo());
        this.processor.setArgs(value.getArgs());
        this.processor.setJvmArgs(value.getJvmArgs());
    }

    @Input
    @Optional
    @Nullable
    public String getProcessorTool() {
        return this.processor == null ? null : this.processor.getVersion();
    }
    public void setProcessorTool(String value) {
        ensureProcessor().setVersion(value);
    }

    @Input
    @Optional
    @Nullable
    public String getProcessorRepo() {
        return this.processor == null ? null : this.processor.getRepo();
    }
    public void setProcessorRepo(String value) {
        ensureProcessor().setRepo(value);
    }

    @Input
    @Optional
    @Nullable
    public List<String> getProcessorArgs() {
        return this.processor == null ? null : this.processor.getArgs();
    }
    public void setProcessorTool(String... values) {
        ensureProcessor().setArgs(Arrays.asList(values));
    }

    @InputFiles
    @Optional
    public Provider<Collection<File>> getProcessorFiles() {
        return this.processorData.map(Map::values);
    }
    public void addProcessorData(String key, File file) {
        this.processorData.put(key, file);
        ensureProcessor().setData(key,  "processor/" + file.getName());
    }

    @Input
    @Optional
    public abstract Property<String> getPatchesOriginalPrefix();

    @Input
    @Optional
    public abstract Property<String> getPatchesModifiedPrefix();

    @Input
    public boolean getNotchObf() {
        return this.notchObf;
    }
    public void setNotchObf(boolean value) {
        this.notchObf = value;
    }

    @Input
    public abstract Property<String> getSourceFileEncoding();

    public void setSourceFileEncoding(Charset value) {
        getSourceFileEncoding().set(value.name());
    }
    public void setSourceFileEncoding(String value) {
        setSourceFileEncoding(Charset.forName(value)); // Load to ensure valid charset.
    }

    @Input
    @Optional
    public abstract ListProperty<String> getUniversalFilters();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

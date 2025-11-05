/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher.tasks;

import com.google.common.io.Files;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class GenerateUserdevConfig extends DefaultTask {

    private final NamedDomainObjectContainer<RunConfig> runs;

    @Nullable
    private DataFunction processor;
    private final MapProperty<String, File> processorData;
    private final Property<String> sourceFileEncoding;
    private final ListProperty<String> extraRuntimeDeps;
    private final ListProperty<String> extraCompileDeps;
    private final ListProperty<String> extraAnnotationProcessorDeps;

    private boolean notchObf = false;

    @Inject
    public GenerateUserdevConfig(final Project project) {
        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));

        ObjectFactory objects = project.getObjects();
        getPatchesOriginalPrefix().convention("a/");
        getPatchesModifiedPrefix().convention("b/");
        sourceFileEncoding = project.getObjects().property(String.class)
                .convention(StandardCharsets.UTF_8.name());
        getInject().convention("inject/");
        getPatches().convention("patches/");

        processorData = objects.mapProperty(String.class, File.class);

        extraRuntimeDeps = objects.listProperty(String.class);
        extraCompileDeps = objects.listProperty(String.class);
        extraAnnotationProcessorDeps = objects.listProperty(String.class);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.json")));
    }

    @TaskAction
    public void apply() throws IOException {
        UserdevConfigV2 json = new UserdevConfigV2(); //TODO: Move this to plugin so we can re-use the names in both tasks?
        json.spec = isV2() ? 2 : 1;
        json.binpatches = "joined.lzma";
        json.sources = getSource().get();
        json.universal = getUniversal().get();
        json.patches = getPatches().get();
        json.inject = getInject().get();
        if (json.inject.isEmpty()) // Workaround since null in properties means use the convention, which we don't want.
            json.inject = null;
        getLibraries().get().forEach(json::addLibrary);
        getModules().get().forEach(json::addModule);
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
            json.extraDependencies = new UserdevConfigV2.ScopedDependencies();
            json.extraDependencies.compileOnly = new ArrayList<>(getExtraCompileDeps().get());
            json.extraDependencies.runtimeOnly = new ArrayList<>(getExtraRuntimeDeps().get());
            json.extraDependencies.annotationProcessor = new ArrayList<>(getExtraAnnotationProcessorDeps().get());
        }

        Files.write(Utils.GSON.toJson(json).getBytes(StandardCharsets.UTF_8), getOutput().get().getAsFile());
    }

    private void addParent(UserdevConfigV1 json, Project project) {
        PatcherExtension patcher = project.getExtensions().findByType(PatcherExtension.class);
        MCPExtension mcp = project.getExtensions().findByType(MCPExtension.class);

        if (patcher != null) {
            if (project != getProject() && patcher.getPatches().isPresent()) {
                // !patches.isPresent() means they don't add anything, used by Forge as a 'clean' workspace
                if (json.parent == null) {
                    json.parent = String.format("%s:%s:%s:userdev", project.getGroup(), project.getName(), project.getVersion());
                    return;
                }
            }
            if (patcher.getParent().isPresent()) {
                addParent(json, patcher.getParent().get());
            }
            //TODO: MCP/Parents without separate projects?
        } else {
            if (json.parent == null) { // Only specify mcp if we have no patcher parent.
                if (mcp == null)
                    throw new IllegalStateException("Could not determine MCP parent for userdev config");
                json.mcp = mcp.getConfig().get().toString();
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
    public abstract ListProperty<String> getModules();

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
    @Optional
    public abstract Property<String> getPatches();

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
    
    private static String depToString(Dependency value) {
        String group = value.getGroup();
        if (group == null)
            throw new IllegalArgumentException("Dependency group cannot be null");

        String version = value.getVersion();
        if (version == null)
            throw new IllegalArgumentException("Dependency version cannot be null");

        return group + ':' + value.getName() + ':' + version;
    }

    @Input
    @Optional
    public ListProperty<String> getExtraCompileDeps() {
        return this.extraCompileDeps;
    }

    public final void addCompileDependency(Dependency value) {
        this.getExtraCompileDeps().add(depToString(value));
    }

    public final void addCompileDependency(Provider<? extends Dependency> value) {
        this.addCompileDependency(value.get());
    }

    public final void addCompileDependency(ProviderConvertible<? extends Dependency> value) {
        this.addCompileDependency(value.asProvider());
    }

    @Input
    @Optional
    public ListProperty<String> getExtraRuntimeDeps() {
        return this.extraRuntimeDeps;
    }

    public final void addRuntimeDependency(Dependency value) {
        this.getExtraRuntimeDeps().add(depToString(value));
    }

    public final void addRuntimeDependency(Provider<? extends Dependency> value) {
        this.addRuntimeDependency(value.get());
    }

    public final void addRuntimeDependency(ProviderConvertible<? extends Dependency> value) {
        this.addRuntimeDependency(value.asProvider());
    }

    @Input
    @Optional
    public ListProperty<String> getExtraAnnotationProcessorDeps() {
        return this.extraAnnotationProcessorDeps;
    }

    public final void addAnnotationProcessorDependency(Dependency value) {
        this.getExtraAnnotationProcessorDeps().add(depToString(value));
    }

    public final void addAnnotationProcessorDependency(Provider<? extends Dependency> value) {
        this.addAnnotationProcessorDependency(value.get());
    }

    public final void addAnnotationProcessorDependency(ProviderConvertible<? extends Dependency> value) {
        this.addAnnotationProcessorDependency(value.asProvider());
    }

    public NamedDomainObjectContainer<RunConfig> runs(@SuppressWarnings("rawtypes") Closure closure) {
        return runs.configure(closure);
    }

    @Input
    public NamedDomainObjectContainer<RunConfig> getRuns() {
        return runs;
    }

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
    public Property<String> getSourceFileEncoding() {
        return this.sourceFileEncoding;
    }

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

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

package net.minecraftforge.gradle.common.tasks;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;

import org.codehaus.groovy.control.io.NullWriter;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Executes the tool JAR.
 *
 * <p>The tool JAR is specified using Maven coordinates, and downloaded using the repositories defined through Gradle.</p>
 */
// TODO: refactor to extend JavaExec?
public abstract class JarExec extends DefaultTask {
    protected boolean hasLog = true;

    private final Provider<File> toolFile;
    private final Provider<String> resolvedVersion;

    protected final Provider<Directory> workDir = getProject().getLayout().getBuildDirectory().dir(getName());
    protected final Provider<RegularFile> logFile = workDir.map(d -> d.file("log.txt"));

    public JarExec() {
        toolFile = getTool().map(toolStr -> MavenArtifactDownloader.gradle(getProject(), toolStr, false));
        resolvedVersion = getTool().map(toolStr -> MavenArtifactDownloader.getVersion(getProject(), toolStr));

        final JavaToolchainSpec toolchain = getProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain();
        getJavaLauncher().convention(getJavaToolchainService().launcherFor(toolchain));
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException("Decorated instance, this should never be thrown unless shenanigans");
    }

    @TaskAction
    public void apply() throws IOException {
        File jar = getToolJar().get();
        File logFile = this.logFile.get().getAsFile();

        // Locate main class in jar file
        JarFile jarFile = new JarFile(jar);
        String mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        jarFile.close();

        // Create parent directory for log file
        Logger logger = getProject().getLogger();
        if (logFile.getParentFile() != null && !logFile.getParentFile().exists() && !logFile.getParentFile().mkdirs()) {
            logger.warn("Could not create parent directory '{}' for log file", logFile.getParentFile().getAbsolutePath());
        }

        final List<String> args = filterArgs(getArgs().get());
        final ConfigurableFileCollection classpath = getProject().files(getToolJar(), getClasspath());
        final File workingDirectory = workDir.get().getAsFile();

        try (PrintWriter log = new PrintWriter(hasLog ? new FileWriter(logFile) : NullWriter.DEFAULT, true)) {
            getProject().javaexec(spec -> {
                spec.setExecutable(getJavaLauncher().get().getExecutablePath());
                spec.setArgs(args);
                spec.setClasspath(classpath);
                spec.setWorkingDir(workingDirectory);
                spec.getMainClass().set(mainClass);

                log.println("Java Launcher: " + spec.getExecutable());
                log.println("Arguments: " + args.stream().collect(Collectors.joining(", ", "'", "'")));
                log.println("Classpath:");
                classpath.forEach(f -> log.println(" - " + f.getAbsolutePath()));
                log.println("Working directory: " + workingDirectory.getAbsolutePath());
                log.println("Main class: " + mainClass);
                log.println("====================================");

                spec.setStandardOutput(new OutputStream() {
                    @Override
                    public void flush() { log.flush(); }
                    @Override
                    public void close() {}
                    @Override
                    public void write(int b) { log.write(b); }
                });
            }).rethrowFailure().assertNormalExitValue();
        }

        if (hasLog) {
            postProcess(logFile);
        }

        // Delete working directory if empty
        final String[] workingDirContents = workingDirectory.list();
        if ((workingDirContents == null || workingDirContents.length == 0) && !workingDirectory.delete()) {
            logger.warn("Could not delete empty working directory '{}'", workingDirectory.getAbsolutePath());
        }
    }

    protected List<String> filterArgs(List<String> args) {
        return args;
    }

    // TODO: remove this? as this isn't used anywhere
    protected void postProcess(File log) {
    }

    protected List<String> replaceArgs(List<String> args, @Nullable Map<String, Object> normalReplacements, @Nullable Multimap<String, Object> multiReplacements) {
        // prevent nulls
        normalReplacements = normalReplacements != null ? normalReplacements : Collections.emptyMap();
        multiReplacements = multiReplacements != null ? multiReplacements : ImmutableMultimap.of();
        if (normalReplacements.isEmpty() && multiReplacements.isEmpty()) return args;

        ArrayList<String> newArgs = new ArrayList<>(args.size());

        // normalReplacements, it is a normal token substitution
        // multiReplacements, it will take the previous token and prepend that to each value for the token

        for (String arg : args) {
            if (multiReplacements.containsKey(arg)) {
                String prefix = newArgs.size() > 1 ? newArgs.remove(newArgs.size() - 1) : null;
                for (Object value : multiReplacements.get(arg)) {
                    if (prefix != null) newArgs.add(prefix);
                    newArgs.add(toString(value));
                }
            } else {
                newArgs.add(toString(normalReplacements.getOrDefault(arg, arg)));
            }
        }

        return newArgs;
    }

    private String toString(Object obj) {
        if (obj instanceof File) {
            return ((File) obj).getAbsolutePath();
        } else if (obj instanceof Path) {
            return ((Path) obj).toAbsolutePath().toString();
        }
        return Objects.toString(obj);
    }

    @Internal
    public String getResolvedVersion() {
        return resolvedVersion.get();
    }

    @Input
    public boolean getHasLog() {
        return hasLog;
    }

    public void setHasLog(boolean value) {
        this.hasLog = value;
    }

    @InputFile
    public Provider<File> getToolJar() {
        return toolFile;
    }

    @Input
    public abstract Property<String> getTool();

    @Input
    public abstract ListProperty<String> getArgs();

    @Optional
    @InputFiles
    public abstract ConfigurableFileCollection getClasspath();

    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.HashMap;
import java.util.List;

public abstract class DynamicJarExec extends JarExec {
    public DynamicJarExec() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
        // Dumb gradle...
        getDataGradle().putAll(getData().map(m -> {
            HashMap<String, FileWrapper> newMap = new HashMap<>();
            m.forEach((k, v) -> newMap.put(k, new FileWrapper(v)));
            return newMap;
        }));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        ImmutableMap.Builder<String, Object> replace = ImmutableMap.builder();
        replace.put("{input}", getInput().get().getAsFile());
        replace.put("{output}", getOutput().get().getAsFile());
        getData().get().forEach((key, value) -> replace.put('{' + key + '}', value.getAbsolutePath()));

        return replaceArgs(args, replace.build(), null);
    }

    @Internal
    public abstract MapProperty<String, File> getData();

    /**
     * @deprecated necessary until <a href="https://github.com/gradle/gradle/issues/8646">Gradle issue #8646</a> is fixed
     */
    @Deprecated
    @Nested
    @Optional
    protected abstract MapProperty<String, FileWrapper> getDataGradle();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    protected static class FileWrapper {
        private final File file;

        protected FileWrapper(File file) {
            this.file = file;
        }

        @InputFile
        public File getFile() {
            return this.file;
        }
    }
}

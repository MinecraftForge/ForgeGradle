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

import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public abstract class ExtractRangeMap extends JarExec {
    private boolean batch = true;

    public ExtractRangeMap() {
        getTool().set(Utils.SRG2SOURCE);
        getArgs().addAll("--extract", "--source-compatibility", "{compat}", "--output", "{output}", "--lib",
                "{library}", "--input", "{input}", "--batch", "{batched}");
        setMinimumRuntimeJavaVersion(11);

        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.txt")));
        final JavaPluginExtension extension = getProject().getExtensions().findByType(JavaPluginExtension.class);
        if (extension != null && extension.getToolchain().getLanguageVersion().isPresent()) {
            int version = extension.getToolchain().getLanguageVersion().get().asInt();
            getSourceCompatibility().convention((version <= 8 ? "1." : "") + version);
        } else {
            getSourceCompatibility().convention("1.8");
        }
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{compat}", getSourceCompatibility().get(),
                "{output}", getOutput().get().getAsFile(),
                "{batched}", batch
                ), ImmutableMap.of(
                "{input}", getSources().getFiles(),
                "{library}", getDependencies().getFiles()
                )
        );
    }

    @InputFiles
    public abstract ConfigurableFileCollection getSources();

    @InputFiles
    public abstract ConfigurableFileCollection getDependencies();

    @OutputFile
    public abstract RegularFileProperty getOutput();

    @Input
    public abstract Property<String> getSourceCompatibility();

    @Input
    public boolean getBatch() {
        return this.batch;
    }

    public void setBatch(boolean value) {
        this.batch = value;
    }
}

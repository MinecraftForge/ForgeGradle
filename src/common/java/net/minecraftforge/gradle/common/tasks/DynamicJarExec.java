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

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.util.List;
import java.util.Map;

public abstract class DynamicJarExec extends JarExec {
    public DynamicJarExec() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        ImmutableMap.Builder<String, Object> replace = ImmutableMap.builder();
        replace.put("{input}", getInput().get().getAsFile());
        replace.put("{output}", getOutput().get().getAsFile());
        getData().get().forEach((key, value) -> replace.put('{' + key + '}', value.getAbsolutePath()));

        return replaceArgs(args, replace.build(), null);
    }

    @InputFiles
    @Optional
    public Provider<Iterable<File>> getDataIterable() {
        return getData().map(Map::values);
    }

    @Internal
    public abstract MapProperty<String, File> getData();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

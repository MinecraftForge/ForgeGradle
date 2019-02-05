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

package net.minecraftforge.gradle.userdev.tasks;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;

import net.minecraftforge.gradle.common.task.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AccessTransformJar extends JarExec {
    private File input;
    private File output;
    private List<File> ats;

    public AccessTransformJar() {
        tool = Utils.ACCESSTRANSFORMER; // AT spec *should* be standardized, it has been for years. So we *shouldn't* need to configure this.
        args = new String[] { "--inJar", "{input}", "--outJar", "{output}", "--logFile", "accesstransform.log"};
    }

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());

        List<String> ret = Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
        ats.forEach(f -> {
            ret.add("--atFile");
            ret.add(f.getAbsolutePath());
        });
        return ret;
    }

    @InputFiles
    public List<File> getAts() {
        return ats;
    }
    public void setAts(Iterable<File> values) {
        if (ats == null)
            ats = new ArrayList<>();
        values.forEach(ats::add);
    }
    public void setAts(File... values) {
        if (ats == null)
            ats = new ArrayList<>();
        for (File value : values)
            ats.add(value);
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

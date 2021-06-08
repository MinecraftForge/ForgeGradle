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

package net.minecraftforge.gradle.mcp.task;

import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;
import net.minecraftforge.srgutils.IRenamer;

public class GenerateSRG extends DefaultTask {
    private File srg;
    private String mapping;
    private IMappingFile.Format format = IMappingFile.Format.TSRG2;
    private boolean notch = false;
    private boolean reverse = false;
    private File output = getProject().file("build/" + getName() + "/output.tsrg");

    @TaskAction
    public void apply() throws IOException {
        File names = findNames(getMappings());
        if (names == null)
            throw new IllegalStateException("Invalid mappings: " + getMappings() + " Could not find archive");

        IMappingFile input = IMappingFile.load(srg);
        if (!getNotch())
            input = input.reverse().chain(input); // Reverse makes SRG->OBF, chain makes SRG->SRG

        McpNames map = McpNames.load(names);
        IMappingFile ret = input.rename(new IRenamer() {
            @Override
            public String rename(IField value) {
                return map.rename(value.getMapped());
            }

            @Override
            public String rename(IMethod value) {
                return map.rename(value.getMapped());
            }
        });

        ret.write(getOutput().toPath(), getFormat(), getReverse());
    }

    private File findNames(String mapping) {
        int idx = mapping.lastIndexOf('_');
        if (idx == -1) return null; //Invalid format
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = MCPRepo.getMappingDep(channel, version);
        return MavenArtifactDownloader.generate(getProject(), desc, false);
    }

    @InputFile
    public File getSrg() {
        return srg;
    }
    public void setSrg(File value) {
        this.srg = value;
    }

    @Input
    public String getMappings() {
        return mapping;
    }
    public void setMappings(String value) {
        this.mapping = value;
    }

    @Input
    public IMappingFile.Format getFormat() {
        return format;
    }
    public void setFormat(IMappingFile.Format value) {
        this.format = value;
    }
    public void setFormat(String value) {
        this.setFormat(IMappingFile.Format.valueOf(value));
    }

    @Input
    public boolean getNotch() {
        return this.notch;
    }
    public void setNotch(boolean value) {
        this.notch = value;
    }

    @Input
    public boolean getReverse() {
        return this.reverse;
    }
    public void setReverse(boolean value) {
        this.reverse = value;
    }

    @OutputFile
    public File getOutput() {
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}

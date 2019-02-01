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

import java.io.File;
import java.io.IOException;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;

public class GenerateSRG extends DefaultTask {
    private File srg;
    private String mapping;
    private MappingFile.Format format = MappingFile.Format.TSRG;
    private boolean reverse;
    private File output = getProject().file("build/" + getName() + "/output.tsrg");

    @TaskAction
    public void apply() throws IOException {
        File names = findNames(getMappings());
        if (names == null)
            throw new IllegalStateException("Invalid mappings: " + getMappings() + " Could not find archive");


        MappingFile obf_to_srg = MappingFile.load(srg);
        MappingFile ret = new MappingFile();
        McpNames map = McpNames.load(names);
        obf_to_srg.getPackages().forEach(e -> ret.addPackage(e.getMapped(), e.getMapped()));
        obf_to_srg.getClasses().forEach(cls -> {
           ret.addClass(cls.getMapped(), cls.getMapped());
           MappingFile.Cls _cls = ret.getClass(cls.getMapped());
           cls.getFields().forEach(fld -> _cls.addField(fld.getMapped(), map.rename(fld.getMapped())));
           cls.getMethods().forEach(mtd -> _cls.addMethod(mtd.getMapped(), mtd.getMappedDescriptor(), map.rename(mtd.getMapped())));
        });

        ret.write(getFormat(), getOutput(), getReverse());
    }

    private File findNames(String mapping) {
        int idx = mapping.lastIndexOf('_');
        if (idx == -1) return null; //Invalid format
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
        return MavenArtifactDownloader.manual(getProject(), desc, false);
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
    public MappingFile.Format getFormat() {
        return format;
    }
    public void setFormat(MappingFile.Format value) {
        this.format = value;
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

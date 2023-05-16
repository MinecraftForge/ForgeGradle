/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.legacy;

import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class FormatSRG extends DefaultTask {
    private final Property<IMappingFile.Format> format;

    public FormatSRG() {
        this.format = getProject().getObjects().property(IMappingFile.Format.class)
                .convention(IMappingFile.Format.SRG);
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(f -> f.file("output.srg")));
    }

    @TaskAction
    public void apply() throws IOException {
        IMappingFile input = IMappingFile.load(getSrg().get().getAsFile());
        input.write(getOutput().get().getAsFile().toPath(), getFormat().get(), false);
    }

    @InputFile
    public abstract RegularFileProperty getSrg();

    @Input
    public Property<IMappingFile.Format> getFormat() {
        return this.format;
    }

    public void setFormat(String value) {
        this.getFormat().set(IMappingFile.Format.valueOf(value));
    }

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

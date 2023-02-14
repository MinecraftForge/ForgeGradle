/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import com.google.common.collect.ImmutableMap;
import java.util.List;

public abstract class RenameJarSrg2Mcp extends JarExec {
    private boolean signatureRemoval = false;

    public RenameJarSrg2Mcp() {
        getTool().set(Utils.INSTALLERTOOLS);
        getArgs().addAll("--task", "SRG_TO_MCP", "--input", "{input}", "--output", "{output}", "--mcp", "{mappings}", "{strip}");
    }

    @Override
    protected List<String> filterArgs(List<String> args) {
        return replaceArgs(args, ImmutableMap.of(
                "{input}", getInput().get().getAsFile(),
                "{output}", getOutput().get().getAsFile(),
                "{mappings}", getMappings().get().getAsFile(),
                "{strip}", signatureRemoval ? "--strip-signatures" : ""), null);
    }

    @Input
    public boolean getSignatureRemoval() {
        return this.signatureRemoval;
    }

    public void setSignatureRemoval(boolean value) {
        this.signatureRemoval = value;
    }

    @InputFile
    public abstract RegularFileProperty getMappings();

    @InputFile
    public abstract RegularFileProperty getInput();

    @OutputFile
    public abstract RegularFileProperty getOutput();
}

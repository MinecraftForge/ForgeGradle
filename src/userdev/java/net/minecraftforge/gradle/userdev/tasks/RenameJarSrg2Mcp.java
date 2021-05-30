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

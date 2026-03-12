/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev;

import net.minecraftforge.gradle.common.util.MinecraftExtension;
import org.gradle.api.Project;

public abstract class UserDevExtension extends MinecraftExtension {
    public static final String EXTENSION_NAME = "minecraft";

    boolean reobfDefault = true;
    private boolean reobf = true;

    public UserDevExtension(final Project project) {
        super(project);
    }

    public void setReobf(boolean value) {
    	this.reobfDefault = false;
        this.reobf = value;
    }

    public boolean getReobf() {
    	return this.reobf;
    }
}

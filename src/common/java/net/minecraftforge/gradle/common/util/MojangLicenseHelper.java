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

package net.minecraftforge.gradle.common.util;

import org.gradle.api.Project;

public class MojangLicenseHelper {
    //TODO: Add a task that people can run to quiet this warning.
    //Also output the specific text from the targeted MC version.
    public static void displayWarning(Project project, String channel) {
        if ("official".equals(channel)) {
            String warning = "WARNING: "
                + "This project is configured to use the official obfuscation mappings provided by Mojang. "
                + "These mapping fall under their associated license, you should be fully aware of this license. "
                + "For the latest license text, refer to the mapping file itself, or the reference copy here: "
                + "https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md";
            project.getLogger().warn(warning);
        }
    }
}

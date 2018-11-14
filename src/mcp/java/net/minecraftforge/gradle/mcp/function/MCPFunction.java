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

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import java.io.File;
import java.util.Map;
import java.util.zip.ZipFile;

public interface MCPFunction {

    default void loadData(Map<String, String> data) {
    }

    default void initialize(MCPEnvironment environment, ZipFile zip) throws Exception {
    }

    File execute(MCPEnvironment environment) throws Exception;

    default void cleanup(MCPEnvironment environment) {
    }

    default void addInputs(HashStore cache, String prefix) {
    }

}

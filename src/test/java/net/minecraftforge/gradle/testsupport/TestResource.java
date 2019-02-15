/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.testsupport;

import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.file.*;

public enum TestResource
{
    ACTUAL_CLEAN_JAR("/net/minecraftforge/gradle/obf/ActualClean.jar"),
    ACTUAL_OBF_JAR("/net/minecraftforge/gradle/obf/ActualObf.jar"),
    ACTUAL_OBF_CSV_JAR("/net/minecraftforge/gradle/obf/ActualObfCsv.jar"),
    OBFUSCATE_SRG("/net/minecraftforge/gradle/obf/obfuscate.srg"),
    METHODS_CSV("/net/minecraftforge/gradle/obf/methods.csv"),
    FIELDS_CSV("/net/minecraftforge/gradle/obf/fields.csv"),
    MERGE_A_ZIP("/net/minecraftforge/gradle/merge/a.zip"),
    MERGE_B_ZIP("/net/minecraftforge/gradle/merge/b.zip"),
    MERGE_EXPECTED_ZIP("/net/minecraftforge/gradle/merge/expected.zip"),
    MOD_WITH_AT("/net/minecraftforge/gradle/at/modWithAt.jar"),
    ORG_EXAMPLE_EXAMPLE_SRC_JAR("/net/minecraftforge/gradle/src/org-example-Example.zip"),;

    final String path;

    TestResource(String path)
    {
        this.path = path;
    }

    public InputStream getInputStream()
    {
        return TestResource.class.getResourceAsStream(path);
    }

    public File getFile(TemporaryFolder temporaryFolder) throws IOException
    {
        File file = temporaryFolder.newFile(new File(path).getName());
        try (InputStream is = getInputStream())
        {
            Files.copy(is, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }
}

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import org.gradle.api.Project;

public class MojangLicenseHelper {

    public static final String HIDE_LICENSE = "hideOfficialWarningUntilChanged";
    public static final String SHOW_LICENSE = "reshowOfficialWarning";

    /**
     * @see #displayWarning(Project, String, String) 
     */
    @Deprecated
    public static void displayWarning(Project project, String channel) {
        displayWarning(project, channel, null);
    }

    public static void displayWarning(Project project, String channel, @Nullable String version, @Nullable String updateChannel, @Nullable String updateVersion) {
        displayWarning(project, channel, version);

        if (updateChannel == null || Objects.equals(channel, updateChannel)) return;

        displayWarning(project, updateChannel, updateVersion);
    }

    public static void displayWarning(Project project, String channel, @Nullable String version) {
        if ("official".equals(channel)) {
            Optional<String> license = version != null ? getOfficialLicense(project, version) : Optional.empty();
            Optional<String> licenseHash = license.map(HashFunction.SHA1::hash);

            if (license.isPresent() && isHidden(project, licenseHash.get())) return;

            String warning = getWarning(license.orElse(null));

            project.getLogger().warn(warning);
            license.map(s -> "WARNING: " + s).ifPresent(project.getLogger()::warn);
        }
    }

    public static void hide(Project project, String channel, String version) {
        if (!"official".equals(channel)) return;

        String hash = getOfficialLicense(project, version)
            .map(HashFunction.SHA1::hash)
            .orElseThrow(() -> new IllegalStateException("Could not get Mojang license text for " + version));

        Path accepted = getLicensePath(project, hash);

        if (Files.exists(accepted)) return;

        try {
            Utils.createEmpty(accepted.toFile());

            String warning = "WARNING: These warnings will not be shown again until the license changes "
                + "or the task `{TASK}` is run.";

            project.getLogger().warn(warning.replace("{TASK}", SHOW_LICENSE));
        } catch (IOException exception) {
            project.getLogger().error("Could not accept Mojang license", exception);
        }
    }

    public static void show(Project project, String channel, String version) {
        if (!"official".equals(channel)) return;

        String hash = getOfficialLicense(project, version)
            .map(HashFunction.SHA1::hash)
            .orElseThrow(() -> new IllegalStateException("Could not get Mojang license text for " + version));

        Path accepted = getLicensePath(project, hash);

        Utils.delete(accepted.toFile());
    }

    private static String getWarning(String license) {
        String warning = "WARNING: "
            + "This project is configured to use the official obfuscation mappings provided by Mojang. "
            + "These mapping fall under their associated license, you should be fully aware of this license. "
            + "For the latest license text, refer {REFER}, or the reference copy here: "
            + "https://github.com/MinecraftForge/MCPConfig/blob/master/Mojang.md"
            + ", You can hide this warning by running the `{TASK}` task";

        return warning
            .replace("{REFER}", license != null ? "below" : "to the mapping file itself")
            .replace("{TASK}", HIDE_LICENSE);
    }

    private static boolean isHidden(Project project, String hash) {
        return Files.exists(getLicensePath(project, hash));
    }

    private static Optional<String> getOfficialLicense(Project project, String version) {
        String minecraftVersion = getMinecraftVersion(version);
        String artifact = "net.minecraft:client:" + minecraftVersion + ":mappings@txt";

        File client = MavenArtifactDownloader.generate(project, artifact, true);

        if (client == null) return Optional.empty();

        try {
            return Optional.of(
                Files.lines(client.toPath())
                    .filter(line -> line.startsWith("#"))        // Only Comments
                    .map(l -> l.substring(1).trim())             // Remove initial #
                    .collect(Collectors.joining("\n"))   // Join via \n
            );
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private static Path getLicensePath(Project project, String hash) {
        return new File(Utils.getCache(project, "licenses"), hash + ".marker").toPath();
    }

    private static final Predicate<String> MCP_CONFIG_TIMESTAMP = Pattern.compile("\\d{8}\\.\\d{6}").asPredicate();

    private static String getMinecraftVersion(String version) {
        int idx = version.lastIndexOf('-');

        if (idx != -1 && MCP_CONFIG_TIMESTAMP.test(version.substring(idx + 1))) {
            version = version.substring(0, idx);
        }

        return version;
    }
}

package net.minecraftforge.gradle.json.version;

import net.minecraftforge.gradle.common.version.Version;

public interface VersionToString {
    String apply(Version version);
}

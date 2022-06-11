package net.minecraftforge.gradle.userdev.util;

public class DeobfuscatingVersionUtils {

    private DeobfuscatingVersionUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: DeobfuscatingVersionUtils. This is a utility class");
    }

    public static String adaptDeobfuscatedVersion(final String version) {
        if (version.contains("_mapped_")) {
            return version.split("_mapped_")[0];
        }

        return version;
    }
}

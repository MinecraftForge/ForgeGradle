/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.jspecify.annotations.NullUnmarked;

import java.util.regex.Pattern;

@NullUnmarked
record ParchmentVersion(
    String timestamp,
    /* @Nullable */ String mcVersion,
    /* @Nullable */ String mapMcVersion
) {
    private static final Pattern TIMESTAMP = Pattern.compile("\\d{4}.\\d{2}.\\d{2}");
    private static final Pattern MCP_TIMESTAMP = Pattern.compile("\\d{8}.\\d{6}");

    // Parchment related things
    public static final String PARCHMENT_MAVEN = "https://maven.parchmentmc.org/";
    private static final String PARCHMENT_GROUP = "org.parchmentmc.data"; // Name is "parchment-{mcversion}'

    /**
     * <pre>
     * Parchment names can be specified in many variants, including some 'shorthand's
     * Basing this implementation on https://parchmentmc.org/docs/getting-started
     *
     * Namely, (in case they change the site)
     *
     * For using Parchment on the same version of Minecraft:
     *    YYYY.MM.DD-&lt;Environment MC version>
     *    Examples:
     *      2021.12.12-1.17.1 for Minecraft 1.17.1
     *      2022.08.07-1.18.2 for Minecraft 1.18.2
     *      2022.08.14-1.19.2 for Minecraft 1.19.2
     *
     * For using Parchment for an older version on a newer MC version
     * 	  &lt;Mapping's MC version>-YYYY.MM.DD-&lt;Environment MC version>
     *	  Examples:
     *      1.17.1-2021.12.12-1.18
     *          Minecraft 1.17.1 mappings (2021.12.12-1.17.1) in an MC 1.18 environment
     *      1.18.2-2022.08.07-1.19.1
     *          Minecraft 1.18.2 mappings (2021.08.07-1.18.2) in an MC 1.19.1 environment
     *      1.19.2-2022.08.14-1.20
     *          Minecraft 1.19.2 mappings (2021.08.14-1.19.2) in an MC 1.20 environment
     *
     * Parchment also publishes 'snapshots', These are not supported by MCMaven, and as such will
     * throw an exception during parsing.
     *     Example:
     *       parchment-1.21.9/2025.10.05-nightly-SNAPSHOT
     *       parchment-1.16.5/BLEEDING-SNAPSHOT
     * They have also published two known versions that don't follow the standard format. And I don't feel like supporting it.
     *     parchment-1.16.5/20210607-SNAPSHOT
     *     parchment-1.16.5/20210608-SNAPSHOT
     *
     * Tho undocumented, the implementation of Parchment's FG6 plugin supported specifying the MCP timestamp as part of the Environment MC version
     * https://github.com/ParchmentMC/Librarian/blob/c7f9878feb76d210aa65569fe38cad5297f439c5/src/main/java/org/parchmentmc/librarian/forgegradle/ParchmentMappingVersion.java#L52
     *
     * In addition to the above formats I DO support not specifying a Minecraft version. It will need to be filled in later
     * to find the correct download, but typically we can pull it from MCPConfig/other context.
     *
     * Note: This does not do any case sanitization
     *
     * @throws IllegalArgumentException if the version fails to parse
     */
    public static ParchmentVersion parse(String version) {
        if (version == null)
            throw new IllegalArgumentException("Parchment mappings version must be present");

    	if (version.contains("-SNAPSHOT"))
            throw new IllegalArgumentException("Parchment snapshots are not supported: " + version);

    	var matcher = TIMESTAMP.matcher(version);
    	if (!matcher.find())
            throw new IllegalArgumentException("Parchment version does not contain a timestamp: " + version);

    	int start = matcher.start();
    	int end = matcher.end();

    	// Simple case, we just specify the timestamp
    	if (start == 0 && end == version.length())
    		return new ParchmentVersion(version, null, null);

		var timestamp = matcher.group();
    	String mcVersion = null;

    	// Minecraft Version is specified
    	if (end < version.length()) {
    		if (version.charAt(end) != '-' || end == version.length() - 1)
    			throw new IllegalArgumentException("Parchment version does not specify Minecraft version: " + version);
    		mcVersion = version.substring(end + 1);
    	}

    	// Default mapping version is the same as mc version
    	var mapMcVersion = stripMcp(mcVersion);

    	// Mappings MC version is specified
    	if (start > 0) {
    		if (version.charAt(start - 1) != '-' || start == 1)
    			throw new IllegalArgumentException("Parchment version does not specify Mapping Minecraft version: " + version);
    		mapMcVersion = version.substring(0, start - 1);
    	}

    	return new ParchmentVersion(timestamp, mcVersion, mapMcVersion);
    }

    /**
     * Variant of {@link #parse(String)} that returns null instead of throwing IllegalArgumentException
     */
    public static ParchmentVersion tryParse(String version) {
    	try {
    		return parse(version);
    	} catch (IllegalArgumentException e) {
    		return null;
    	}
    }

	// Remove MCP timestamp if its on there.
    private static String stripMcp(String version) {
    	if (version == null)
    		return null;
    	if (version.length() <= 17 || version.charAt(version.length() - 16) != '-')
    		return version;

    	var matcher = MCP_TIMESTAMP.matcher(version);
    	if (matcher.find() && matcher.start() == version.length() - 15)
    		return version.substring(0, version.length() - 16);

    	return version;
    }

    public ParchmentVersion withMinecraft(String mcVersion) {
    	return new ParchmentVersion(timestamp, mcVersion, mapMcVersion == null ? mcVersion : mapMcVersion);
    }

    public String toFriendly() {
    	if (mapMcVersion == null) {
    		if (mcVersion == null)
    			return timestamp;
    		return timestamp + '-' + mcVersion;
    	}

    	if (mcVersion == null)
    		return mapMcVersion + '-' + timestamp;

    	if (mapMcVersion.equals(stripMcp(mcVersion)))
    		return timestamp + '-' + mcVersion;

    	return mapMcVersion + '-' + timestamp + '-' + mcVersion;
    }
}

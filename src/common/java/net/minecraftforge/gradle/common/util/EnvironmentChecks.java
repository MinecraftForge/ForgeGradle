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
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.IOException;
import java.net.URL;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/**
 * Various environment checks.
 *
 * @see #checkEnvironment(Project)
 */
public class EnvironmentChecks {
    public static final String ENABLE_CERTIFICATE_CHECK_VARIABLE = "net.minecraftforge.gradle.check.certs";
    public static final String ENABLE_GRADLE_CHECK_VARIABLE = "net.minecraftforge.gradle.check.gradle";
    public static final String ENABLE_JAVA_CHECK_VARIABLE = "net.minecraftforge.gradle.check.java";

    private static final boolean ENABLE_CERTIFICATE_CHECK = Boolean.parseBoolean(System.getProperty(ENABLE_CERTIFICATE_CHECK_VARIABLE, "true"));
    private static final boolean ENABLE_GRADLE_CHECK = Boolean.parseBoolean(System.getProperty(ENABLE_GRADLE_CHECK_VARIABLE, "true"));
    private static final boolean ENABLE_JAVA_CHECK = Boolean.parseBoolean(System.getProperty(ENABLE_JAVA_CHECK_VARIABLE, "true"));
    private static final Marker ENV_CHECK = MarkerFactory.getMarker("forgegradle.env_check");

    public static void checkJavaRange(@Nullable JavaVersionParser.JavaVersion minVersionInclusive, @Nullable JavaVersionParser.JavaVersion maxVersionExclusive) {
        checkRange("java", JavaVersionParser.getCurrentJavaVersion(), minVersionInclusive, maxVersionExclusive, "", "");
    }

    public static void checkGradleRange(@Nullable GradleVersion minVersionInclusive, @Nullable GradleVersion maxVersionExclusive) {
        checkRange("Gradle", GradleVersion.current(), minVersionInclusive, maxVersionExclusive,
                "\nNote: Upgrade your gradle version first before trying to switch to FG5.", "");
    }

    private static <T> void checkRange(String name, Comparable<T> current, @Nullable T minVersionInclusive, @Nullable T maxVersionExclusive, String additionalMin, String additionalMax) {
        if (minVersionInclusive != null && current.compareTo(minVersionInclusive) < 0) {
            throw new EnvironmentCheckFailedException(String.format("Found %s version %s. Minimum required is %s.%s", name, current, minVersionInclusive, additionalMin));
        }

        if (maxVersionExclusive != null && current.compareTo(maxVersionExclusive) >= 0) {
            throw new EnvironmentCheckFailedException(String.format("Found %s version %s. Versions %s and newer are not supported yet.%s", name, current, maxVersionExclusive, additionalMax));
        }
    }

    /**
     * Checks the current project environment, and throws an exception if not satisfied.
     *
     * Current environment checks:
     * <ul>
     *     <li>Java version is <em>1.8.0_101</em> or above (first JDK version to include Let's Encrypt certificates)</li>
     *     <li>Gradle version is <em>7.1</em> or above (minimum version required by ForgeGradle)</li>
     *     <li>Certificates for {@link Utils#FORGE_MAVEN} and {@link Utils#MOJANG_MAVEN} are valid (required repositories)</li>
     * </ul>
     *
     * @see #checkGradleRange(GradleVersion, GradleVersion)
     * @see #checkJavaRange(JavaVersionParser.JavaVersion, JavaVersionParser.JavaVersion)
     * @see #testServerConnection(String)
     */
    public static void checkEnvironment(Project project) {
        Logger logger = project.getLogger();
        if (ENABLE_JAVA_CHECK) {
            logger.debug(ENV_CHECK, "Checking Java version");
            checkJavaRange(
                    // Minimum must be update 101 as it's the first one to include Let's Encrypt certificates.
                    JavaVersionParser.parseJavaVersion("1.8.0_101"),
                    null
            );
        } else {
            logger.debug(ENV_CHECK, "Java version check disabled by system property");
        }

        if (ENABLE_GRADLE_CHECK) {
            logger.debug(ENV_CHECK, "Checking Gradle version");
            checkGradleRange(
                    GradleVersion.version("7.1"),
                    null
            );
        } else {
            logger.debug(ENV_CHECK, "Gradle version check disabled by system property");
        }

        if (ENABLE_CERTIFICATE_CHECK) {
            logger.debug(ENV_CHECK, "Checking server connections");
            testServerConnection(Utils.FORGE_MAVEN);
            testServerConnection(Utils.MOJANG_MAVEN);
        } else {
            logger.debug(ENV_CHECK, "Server connection check disabled by system property");
        }
    }

    private static void testServerConnection(String url) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.connect();
            conn.getResponseCode();
        } catch (SSLException e) {
            throw new EnvironmentCheckFailedException(String.format("Failed to validate certificate for host '%s'. "
                    + "To disable this check, re-run with '-D%s=false'.", url, ENABLE_CERTIFICATE_CHECK_VARIABLE), e);
        } catch (IOException e) {
            // Normal connection failed, not the point of this test so ignore
        }
    }

    /**
     * Exception thrown when an environment check fails.
     */
    static class EnvironmentCheckFailedException extends RuntimeException {
        EnvironmentCheckFailedException(String message) {
            super(message);
        }

        EnvironmentCheckFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

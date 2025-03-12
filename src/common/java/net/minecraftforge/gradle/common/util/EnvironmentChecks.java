/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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
 * Utility for common environment variables to toggle various features in FG.
 * <p>
 * These can be configured using the -D{Name}={true|false} to set the system property.
 * <p>
 * Various environment checks, such as Java version, Gradle version, and certificate validation.
 * @see #checkEnvironment(Project)
 */
public class EnvironmentChecks {
    public static final String ENABLE_CERTIFICATE_CHECK_VARIABLE = "net.minecraftforge.gradle.check.certs";
    public static final String ENABLE_GRADLE_CHECK_VARIABLE = "net.minecraftforge.gradle.check.gradle";
    public static final String ENABLE_JAVA_CHECK_VARIABLE = "net.minecraftforge.gradle.check.java";
    private static final String FILTER_REPOS_VARIABLE = "net.minecraftforge.gradle.filter.repos";
    private static final String INVALIDATE_CACHE_VARIABLE = "net.minecraftforge.gradle.invalidate.cache";
    private static final String DEBUG_REPOS_VARIABLE      = "net.minecraftforge.gradle.repo.debug";
    private static final String ENABLE_SOURCES_VARIABLE   = "net.minecraftforge.gradle.repo.sources";
    private static final String ENABLE_RECOMPILE_VARIABLE = "net.minecraftforge.gradle.repo.recompile";
    private static final String RECOMPILE_ARGS_VARIABLE   = "net.minecraftforge.gradle.repo.recompile.args";
    private static final String ENABLE_RECOMPILE_FORK_VARIABLE  = "net.minecraftforge.gradle.repo.recompile.fork";
    private static final String RECOMPILE_FORK_ARGS_VARIABLE    = "net.minecraftforge.gradle.repo.recompile.fork.args";
    private static final String AUTOMATIC_ATTACH_REPOS_VARIABLE = "net.minecraftforge.gradle.repo.attach";

    private static final EnvironmentFlag ENABLE_CERTIFICATE_CHECK = new EnvironmentFlag(ENABLE_CERTIFICATE_CHECK_VARIABLE, true);
    private static final EnvironmentFlag ENABLE_GRADLE_CHECK = new EnvironmentFlag(ENABLE_GRADLE_CHECK_VARIABLE, true);
    private static final EnvironmentFlag ENABLE_JAVA_CHECK = new EnvironmentFlag(ENABLE_JAVA_CHECK_VARIABLE, true);

    /**
     * Attempts to filter all repositories to not include any 'mapped' dependencies, this should
     * speed up dependency resolution by not having it check public repositories for things we create.
     * <p>
     * Specifically it filters anything with a version that matches `.*_mapped_.*`
     * <p>
     * Environment Flag: {@value #FILTER_REPOS_VARIABLE}
     */
    public static final EnvironmentFlag FILTER_REPOS = new EnvironmentFlag(FILTER_REPOS_VARIABLE, true);

    /**
     * Forces anything that uses {@link net.minecraftforge.gradle.common.util.HashStore HashStore} to miss
     * the cache this run, forcing all tasks and dependencies to be re-evaluated.
     * <p>
     * This is typically only used when developing ForgeGradle itself, as we would want to bust the cache
     * when the code changes.
     * <p>
     * Environment Flag: {@value #INVALIDATE_CACHE_VARIABLE}
     */
    public static final EnvironmentFlag INVALIDATE_CACHE = new EnvironmentFlag(INVALIDATE_CACHE_VARIABLE, false);

    /**
     * Enables debugging for repositories, this will log a lot of information about what the repositories are doing.
     * <p>
     * Environment Flag: {@value #DEBUG_REPOS_VARIABLE}
     */
    public static final EnvironmentFlag DEBUG_REPOS = new EnvironmentFlag(DEBUG_REPOS_VARIABLE, false);

    /**
     * Enables generating source artifacts in our dynamic repositories. Disabling this is recommended for
     * build services where the source code is not needed.<br>
     * Default is true.
     * <p>
     * Environment Flag: {@value #ENABLE_SOURCES_VARIABLE}
     */
    public static final EnvironmentFlag ENABLE_SOURCES = new EnvironmentFlag(ENABLE_SOURCES_VARIABLE, true);

    /**
     * Enables recompiling Minecraft's source into a jar and serving it from the User Repo after the source
     * has been requested. You can disable this if you don't care about line numbers matching between source
     * and binary. <br>
     * Default is true.
     * <p>
     * Environment Flag: {@value #ENABLE_RECOMPILE_VARIABLE}
     */
    public static final EnvironmentFlag ENABLE_RECOMPILE = new EnvironmentFlag(ENABLE_RECOMPILE_VARIABLE, true);

    /**
     * Additional arguments to pass to the recompile process in the UserDev repo. Added in cause you need to
     * add more memory or something. <br>
     * Default is null.
     * <p>
     * Environment Value: {@value #RECOMPILE_ARGS_VARIABLE}
     */
    public static final EnvironmentValue RECOMPILE_ARGS = new EnvironmentValue(RECOMPILE_ARGS_VARIABLE, null);

    /**
     * Enables forking the recompile process into a separate JVM. <br>
     * Default is false.
     * <p>
     * Environment Flag: {@value #ENABLE_RECOMPILE_FORK_VARIABLE}
     */
    public static final EnvironmentFlag ENABLE_RECOMPILE_FORK = new EnvironmentFlag(ENABLE_RECOMPILE_FORK_VARIABLE, false);

    /**
     * Additional arguments to pass to the recompile process in the UserDev repo when being forked. Added in cause you need to
     * add more memory or something. <br>
     * Default is null.
     * <p>
     * Environment Value: {@value #RECOMPILE_FORK_ARGS_VARIABLE}
     */
    public static final EnvironmentValue RECOMPILE_FORK_ARGS = new EnvironmentValue(RECOMPILE_FORK_ARGS_VARIABLE, null);

    /**
     * This controls if the Forge, MavenCentral, and Mojang repositories are automatically attached to the project.
     * It is usually done during afterEvaluate, but can be disabled if you want to manage yourself.
     * <p>
     * Default is true.
     * <p>
     * Environment Flag: {@value #AUTOMATIC_ATTACH_REPOS_VARIABLE}
     */
    public static final EnvironmentFlag AUTOMATIC_ATTACH_REPOS = new EnvironmentFlag(AUTOMATIC_ATTACH_REPOS_VARIABLE, true);

    private static final Marker ENV_CHECK = MarkerFactory.getMarker("forgegradle.env_check");

    public static final class EnvironmentFlag {
        private final String key;
        private final String simpleKey;
        private final boolean _default;

        private EnvironmentFlag(String key, boolean _default) {
            this.key = key;
            this.simpleKey = key.replace("net.minecraftforge.gradle.", "fg.");
            this._default = _default;
        }

        public boolean isEnabled() {
            String           val = System.getProperty(key);
            if (val == null) val = System.getProperty(simpleKey);
            //if (val == null) val = System.getenv(key);
            //if (val == null) val = System.getenv(simpleKey);
            return val == null ? _default : Boolean.parseBoolean(val);
        }
    }

    public static final class EnvironmentValue {
        private final String key;
        private final String simpleKey;
        private final String _default;

        private EnvironmentValue(String key, String _default) {
            this.key = key;
            this.simpleKey = key.replace("net.minecraftforge.gradle.", "fg.");
            this._default = _default;
        }

        public String getValue() {
            String           val = System.getProperty(key);
            if (val == null) val = System.getProperty(simpleKey);
            return val == null ? _default : val;
        }
    }

    public static void checkJavaRange(@Nullable JavaVersionParser.JavaVersion minVersionInclusive, @Nullable JavaVersionParser.JavaVersion maxVersionExclusive) {
        checkRange("java", JavaVersionParser.getCurrentJavaVersion(), minVersionInclusive, maxVersionExclusive, "", "");
    }

    public static void checkGradleRange(@Nullable GradleVersion minVersionInclusive, @Nullable GradleVersion maxVersionExclusive) {
        checkRange("Gradle", GradleVersion.current(), minVersionInclusive, maxVersionExclusive,
                "\nNote: Upgrade your gradle version first before trying to switch to FG6.", "");
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
     *     <li>Gradle version is <em>8.1</em> or above (minimum version required by ForgeGradle)</li>
     *     <li>Gradle version is below <em>9.0</em> (ForgeGradle 6 only supports the Gradle 8.x series)</li>
     *     <li>Certificates for {@link Utils#FORGE_MAVEN} and {@link Utils#MOJANG_MAVEN} are valid (required repositories)</li>
     * </ul>
     *
     * @see #checkGradleRange(GradleVersion, GradleVersion)
     * @see #checkJavaRange(JavaVersionParser.JavaVersion, JavaVersionParser.JavaVersion)
     * @see #testServerConnection(String)
     */
    public static void checkEnvironment(Project project) {
        Logger logger = project.getLogger();
        if (ENABLE_JAVA_CHECK.isEnabled()) {
            logger.debug(ENV_CHECK, "Checking Java version");
            checkJavaRange(
                    // Minimum must be update 101 as it's the first one to include Let's Encrypt certificates.
                    JavaVersionParser.parseJavaVersion("1.8.0_101"),
                    null
            );
        } else {
            logger.debug(ENV_CHECK, "Java version check disabled by system property");
        }

        if (ENABLE_GRADLE_CHECK.isEnabled()) {
            logger.debug(ENV_CHECK, "Checking Gradle version");
            checkGradleRange(
                    GradleVersion.version("8.1"),
                    GradleVersion.version("9.0")
            );
        } else {
            logger.debug(ENV_CHECK, "Gradle version check disabled by system property");
        }

        if (ENABLE_CERTIFICATE_CHECK.isEnabled()) {
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
    @SuppressWarnings("serial")
    static class EnvironmentCheckFailedException extends RuntimeException {
        EnvironmentCheckFailedException(String message) {
            super(message);
        }

        EnvironmentCheckFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

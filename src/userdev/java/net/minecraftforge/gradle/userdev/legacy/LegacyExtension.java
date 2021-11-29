package net.minecraftforge.gradle.userdev.legacy;

import groovy.lang.GroovyObjectSupport;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.srgutils.MinecraftVersion;
import org.gradle.api.Project;


/**
 * Provides an extension block named "legacy".
 *
 * <code>
 * legacy {
 *     flag = set;
 * }</code>
 *
 * It allows for configuring RetroGradle patches and fixes, which are otherwise set by version.
 * Each fix is documented in this class and in the application function.
 * {@link UserDevPlugin#runRetrogradleFixes()}
 *
 * @author Curle
 */
public class LegacyExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "legacy";

    public LegacyExtension(Project project) {
        final MinecraftVersion version = MinecraftVersion.from((String) project.getExtensions().getExtraProperties().get("MC_VER"));
        final MinecraftVersion FG2_3 = MinecraftVersion.from("1.12.2");

        // fixClasspath by default if version is below FG 2.3
        fixClasspath = version.compareTo(FG2_3) < 0;
    }

    /**
     * fixClassPath;
     *  FG2 Userdev puts all classes and resources into a single jar file for FML to consume.
     *  FG3+ puts classes and resources into separate folders, which breaks on older versions.
     *  We replicate the FG2 behavior by forcing the classes and resources to go to the same build folder.
     *
     * Takes a boolean - true for apply fix, false for no fix.
     */
    private boolean fixClasspath;

    // fixClasspath Getter and buildscript integration.
    public boolean getFixClasspath() { return fixClasspath; }

    // fixClasspath Setter
    public void setFixClasspath(boolean shouldFix) {
        fixClasspath = shouldFix;
    }
}

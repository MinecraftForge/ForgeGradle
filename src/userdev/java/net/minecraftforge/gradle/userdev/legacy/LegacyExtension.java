package net.minecraftforge.gradle.userdev.legacy;

import groovy.lang.GroovyObjectSupport;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.srgutils.MinecraftVersion;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;


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
 * {@link UserDevPlugin#runRetrogradleFixes}
 *
 * @author Curle
 */
public abstract class LegacyExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "legacy";
    private static final MinecraftVersion FG3 = MinecraftVersion.from("1.13");

    public LegacyExtension(Project project) {
        getFixClasspath().convention(project.provider(() -> {
            final MinecraftVersion version = MinecraftVersion.from((String) project.getExtensions().getExtraProperties().get("MC_VERSION"));
            
            // fixClasspath by default if version is below FG 3
            return version.compareTo(FG3) < 0;
        }));
    }

    /**
     * fixClassPath;
     *  FG2 Userdev puts all classes and resources into a single jar file for FML to consume.
     *  FG3+ puts classes and resources into separate folders, which breaks on older versions.
     *  We replicate the FG2 behavior by replacing these folders by the jar artifact on the runtime classpath.
     *
     * Takes a boolean - true for apply fix, false for no fix.
     */
    public abstract Property<Boolean> getFixClasspath();
}

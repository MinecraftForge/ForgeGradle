package net.minecraftforge.gradle.forgedev;

import net.minecraftforge.gradle.patcher.PatcherPlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import javax.annotation.Nonnull;

public class ForgeDevPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        // Require patcher to be applied first
        if(!project.getPlugins().hasPlugin(PatcherPlugin.class)) {
            project.getPlugins().apply(PatcherPlugin.class);
        }
    }

}

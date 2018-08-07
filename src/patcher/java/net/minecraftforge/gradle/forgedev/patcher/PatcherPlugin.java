package net.minecraftforge.gradle.forgedev.patcher;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;

public class PatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        project.getExtensions().create("patcher", PatcherExtension.class, project);
    }

}

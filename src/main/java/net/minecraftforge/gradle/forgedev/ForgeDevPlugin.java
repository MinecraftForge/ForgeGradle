package net.minecraftforge.gradle.forgedev;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.annotation.Nonnull;

public class ForgeDevPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        project.getExtensions().create("forgegradle", ForgeDevExtension.class, project.getObjects());
    }

}

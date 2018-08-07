package net.minecraftforge.gradle.forgedev.patcher;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;

public class PatcherExtension {

    public File cleanSrc;
    public File patchedSrc;
    public File patches;

    @Inject
    public PatcherExtension(Project project) {
    }

}

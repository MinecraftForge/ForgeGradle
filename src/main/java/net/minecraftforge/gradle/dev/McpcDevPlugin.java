package net.minecraftforge.gradle.dev;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;

public class McpcDevPlugin extends DevBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // set fmlDir
        getExtension().setFmlDir("forge/fml");
        getExtension().setForgeDir("forge");
        getExtension().setBukkitDir("bukkit");

//        // the master setup task.
//        Task task = makeTask("setupMcpc", DefaultTask.class);
//        task.dependsOn("extractForgeSources", "generateProjects", "eclipse", "copyAssets");
//        task.setGroup("Forge");
//
//        // the master task.
//        task = makeTask("buildPackages");
//        task.dependsOn("launch4j", "createChangelog", "packageUniversal", "packageInstaller", "packageUserDev", "packageSrc", "genJavadocs");
//        task.setGroup("Forge");
    }
}

package net.minecraftforge.gradle.userdev;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DownloadMCMeta;
import net.minecraftforge.gradle.common.task.DownloadMavenArtifact;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.EclipseHacks;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.userdev.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public class UserDevPlugin implements Plugin<Project> {
    private static String MINECRAFT = "minecraft";

    @Override
    public void apply(@Nonnull Project project) {
        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create("minecraft", UserDevExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File natives_folder = project.file("build/natives/");

        NamedDomainObjectContainer<RenameJarInPlace> reobf = project.container(RenameJarInPlace.class, new NamedDomainObjectFactory<RenameJarInPlace>() {
            @Override
            public RenameJarInPlace create(String jarName) {
                String name = Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);
                JavaPluginConvention java = (JavaPluginConvention)project.getConvention().getPlugins().get("java");

                final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);
                task.setClasspath(java.getSourceSets().getByName("main").getCompileClasspath());

                project.getTasks().getByName("assemble").dependsOn(task);

                // do after-Evaluate resolution, for the same of good error reporting
                project.afterEvaluate(p -> {
                    Task jar = project.getTasks().getByName(jarName);
                    if (!(jar instanceof Jar))
                        throw new IllegalStateException(jarName + "  is not a jar task. Can only reobf jars!");
                    task.setInput(((Jar)jar).getArchivePath());
                    task.dependsOn(jar);
                });

                return task;
            }
        });
        project.getExtensions().add("reobf", reobf);

        Configuration minecraft = project.getConfigurations().maybeCreate(MINECRAFT);
        Configuration compile = project.getConfigurations().maybeCreate("compile");
        compile.extendsFrom(minecraft);

        TaskProvider<DownloadMavenArtifact> downloadMcpConfig = project.getTasks().register("downloadMcpConfig", DownloadMavenArtifact.class);
        TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register("extractSrg", ExtractMCPData.class);
        TaskProvider<GenerateSRG> createMcpToSrg = project.getTasks().register("createMcpToSrg", GenerateSRG.class);
        TaskProvider<DownloadMCMeta> downloadMCMeta = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);

        extractSrg.configure(task -> {
            task.dependsOn(downloadMcpConfig);
            task.setConfig(downloadMcpConfig.get().getOutput());
        });

        createMcpToSrg.configure(task -> {
            task.setReverse(true);
            task.dependsOn(extractSrg);
            task.setSrg(extractSrg.get().getOutput());
            task.setMappings(extension.getMappings());
        });

        extractNatives.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
            task.setOutput(natives_folder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
        });

        project.afterEvaluate(p -> {
            MinecraftUserRepo mcrepo = null;

            //TODO: UserDevRepo deobf = new UserDevRepo(project);

            DependencySet deps = minecraft.getDependencies();
            for (Dependency dep : deps.stream().collect(Collectors.toList())) {
                if (!(dep instanceof ExternalModuleDependency))
                    throw new IllegalArgumentException("minecraft dependency must be a maven dependency.");
                if (mcrepo != null)
                    throw new IllegalArgumentException("Only allows one minecraft dependancy.");
                deps.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), extension.getAccessTransformers(), extension.getMappings());
                String newDep = mcrepo.getDependencyString();
                p.getLogger().lifecycle("New Dep: " + newDep);
                ExternalModuleDependency ext = (ExternalModuleDependency)p.getDependencies().create(newDep);
                {
                    ext.setChanging(true); //TODO: Remove when not in dev
                    minecraft.resolutionStrategy(strat -> {
                        strat.cacheChangingModulesFor(0, TimeUnit.MINUTES);
                    });
                }
                minecraft.getDependencies().add(ext);
            }

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                .add(mcrepo)
                .add(MCPRepo.create(project))
                .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                .attach(project);
            project.getRepositories().maven(e -> {
                e.setUrl("http://files.minecraftforge.net/maven/");
            });
            project.getRepositories().maven(e -> {
                e.setUrl("https://libraries.minecraft.net/");
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
            if (mcrepo == null)
                throw new IllegalStateException("Missing 'minecraft' dependency entry.");
            mcrepo.validate(); //This will set the MC_VERSION property.

            String mcVer = (String)project.getExtensions().getExtraProperties().get("MC_VERSION");
            String mcpVer = (String)project.getExtensions().getExtraProperties().get("MCP_VERSION");
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().setMCVersion(mcVer);

            RenameJarInPlace reobfJar  = reobf.create("jar");
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.setMappings(createMcpToSrg.get().getOutput());

            EclipseHacks.doEclipseFixes(project, extractNatives.get(), downloadAssets.get(), extension.getClientRun(), extension.getServerRun());
        });
    }

}

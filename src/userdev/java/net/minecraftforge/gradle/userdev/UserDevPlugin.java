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

package net.minecraftforge.gradle.userdev;

import net.minecraftforge.gradle.common.util.*;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DownloadMCMeta;
import net.minecraftforge.gradle.common.task.DownloadMavenArtifact;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.userdev.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

public class UserDevPlugin implements Plugin<Project> {
    private static String IMPLEMENTATION_LC = "implementation";
    private static String IMPLEMENTATION_CP        = "Implementation";
    private static String MINECRAFT         = "Minecraft";
    private static String DEOBF             = "Deobf";

    @Override
    public void apply(@Nonnull Project project) {
        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create("minecraft", UserDevExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File natives_folder = project.file("build/natives/");

        final JavaPluginConvention javaPluginConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        final Collection<SourceSet> sourceSets = javaPluginConvention.getSourceSets();

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

        configureSourceSetDefaults(javaPluginConvention);

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
            //Collect all minecraft / deobf configurations
            final List<Configuration> minecraftConfigurations = sourceSets.stream()
                                                                  .map(sourceSet -> p.getConfigurations().maybeCreate(Utils.uncapitalizeString(sourceSet.getImplementationConfigurationName().replace(IMPLEMENTATION_LC, MINECRAFT).replace(IMPLEMENTATION_CP, MINECRAFT))))
                                                                  .collect(Collectors.toList());

            final List<Configuration> deobfConfigurations = sourceSets.stream()
                                                              .map(sourceSet -> p.getConfigurations().maybeCreate(Utils.uncapitalizeString(sourceSet.getImplementationConfigurationName().replace(IMPLEMENTATION_LC, DEOBF).replace(IMPLEMENTATION_CP, DEOBF))))
                                                              .collect(Collectors.toList());

            final List<Configuration> validMinecraftConfigurations = this.validateMinecraftDependencies(minecraftConfigurations, p.getLogger());

            final MinecraftUserRepo mcrepo = generateMinecraftUserRepo(p, extension, validMinecraftConfigurations);
            final ExternalModuleDependency deobfuscatedMinecraftDependency = generateDeobfuscatedMinecraftDependency(mcrepo, logger);

            //TODO: Remove when not in dev/testing anymore
            deobfuscatedMinecraftDependency.setChanging(true);

            validMinecraftConfigurations.forEach(
              minecraftConfiguration -> {
                  DependencySet deps = minecraftConfiguration.getDependencies();
                  //This only contains one dependency, since it has been validated.
                  for (Dependency dep : new ArrayList<>(deps)) {
                      deps.remove(dep);
                      {
                          //TODO: Remove when not in dev/testing anymore
                          minecraftConfiguration.resolutionStrategy(strat -> {
                              strat.cacheChangingModulesFor(10, TimeUnit.SECONDS);
                          });
                      }
                      minecraftConfiguration.getDependencies().add(deobfuscatedMinecraftDependency);
                  }
              }
            );

            final ModRemapingRepo deobfrepo = new ModRemapingRepo(p, extension.getMappings());
            deobfConfigurations.forEach(
              deobfConfiguration -> {
                  final DependencySet deps = deobfConfiguration.getDependencies();
                  for (Dependency dep : new ArrayList<>(deps)) {
                      if (!(dep instanceof ExternalModuleDependency)) //TODO: File deps as well.
                          throw new IllegalArgumentException("deobf dependency must be a maven dependency. File deps are on the TODO");
                      deps.remove(dep);

                      String newDep = deobfrepo.addDep(dep.getGroup(), dep.getName(), dep.getVersion()); // Classifier?
                      deobfConfiguration.getDependencies().add(p.getDependencies().create(newDep));
                  }
              }
            );

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                .add(mcrepo)
                .add(deobfrepo)
                .add(MCPRepo.create(p))
                .add(MinecraftRepo.create(p)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                .attach(p);
            p.getRepositories().maven(e -> {
                e.setUrl(Utils.FORGE_MAVEN);
            });
            p.getRepositories().maven(e -> {
                e.setUrl("https://libraries.minecraft.net/");
                e.metadataSources(src -> src.artifact());
            });
            p.getRepositories().mavenCentral(); //Needed for MCP Deps
            mcrepo.validate(validMinecraftConfigurations, extension.getRuns(), extractNatives.get().getOutput(), downloadAssets.get().getOutput()); //This will set the MC_VERSION property.

            String mcVer = (String)p.getExtensions().getExtraProperties().get("MC_VERSION");
            String mcpVer = (String)p.getExtensions().getExtraProperties().get("MCP_VERSION");
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().setMCVersion(mcVer);

            RenameJarInPlace reobfJar  = reobf.create("jar");
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.setMappings(createMcpToSrg.get().getOutput());

            createRunConfigsTasks(p, extractNatives.get(), downloadAssets.get(), extension.getRuns());
        });
    }

    private void createRunConfigsTasks(@Nonnull Project project, ExtractNatives extractNatives, DownloadAssets downloadAssets, Map<String, RunConfig> runs)
    {
        project.getTasks().withType(GenerateEclipseClasspath.class, t -> { t.dependsOn(extractNatives, downloadAssets); });
        // Utility task to abstract the prerequisites when using the intellij run generation
        TaskProvider<Task> prepareRun = project.getTasks().register("prepareRun", Task.class);
        prepareRun.configure(task -> {
            task.dependsOn(project.getTasks().getByName("classes"), extractNatives, downloadAssets);
        });

        VersionJson json = null;

        try {
            json = Utils.loadJson(extractNatives.getMeta(), VersionJson.class);
        }
        catch (IOException e) {}

        List<String> additionalClientArgs = json != null ? json.getPlatformJvmArgs() : Collections.emptyList();

        runs.forEach((name, runConfig) -> {
            if (runConfig.isClient())
                runConfig.jvmArgs(additionalClientArgs);

            String taskName = name.replaceAll("[^a-zA-Z0-9\\-_]","");
            if (!taskName.startsWith("run"))
                taskName = "run" + taskName.substring(0,1).toUpperCase() + taskName.substring(1);
            TaskProvider<JavaExec> runTask = project.getTasks().register(taskName, JavaExec.class);
            runTask.configure(task -> {
                task.dependsOn(prepareRun.get());

                task.setMain(runConfig.getMain());
                task.setArgs(runConfig.getArgs());
                task.systemProperties(runConfig.getProperties());
                task.environment(runConfig.getEnvironment());
                task.jvmArgs(runConfig.getJvmArgs());

                String workDir = runConfig.getWorkingDirectory();
                File file = new File(workDir);
                if(!file.exists())
                    file.mkdirs();

                task.setWorkingDir(workDir);

                JavaPluginConvention java = (JavaPluginConvention)project.getConvention().getPlugins().get("java");
                task.setClasspath(java.getSourceSets().getByName("main").getRuntimeClasspath());
            });
        });

        EclipseHacks.doEclipseFixes(project, extractNatives, downloadAssets, runs);
        IntellijUtils.createIntellijRunsTask(project, extractNatives, downloadAssets, prepareRun.get(), runs);
    }

    private void configureSourceSetDefaults(JavaPluginConvention javaPluginConvention)
    {
        final Project project = javaPluginConvention.getProject();
        final ConfigurationContainer configurations = project.getConfigurations();

        javaPluginConvention.getSourceSets().all(sourceSet -> {
            final String implementationConfigurationName = sourceSet.getImplementationConfigurationName();
            final Configuration implementationSourceSetConfiguration = configurations.maybeCreate(implementationConfigurationName);

            final String minecraftConfigurationName = Utils.uncapitalizeString(implementationConfigurationName.replace(IMPLEMENTATION_LC, MINECRAFT).replace(IMPLEMENTATION_CP, MINECRAFT));
            final String deobfuscatedConfigurationName = Utils.uncapitalizeString(implementationConfigurationName.replace(IMPLEMENTATION_LC, MINECRAFT).replace(IMPLEMENTATION_CP, MINECRAFT));
            final String sourceSetName = sourceSet.toString();

            Configuration minecraftConfiguration = configurations.maybeCreate(minecraftConfigurationName);
            minecraftConfiguration.setVisible(false);
            minecraftConfiguration.setDescription("Minecraft dependencies for " + sourceSetName + ".");
            minecraftConfiguration.setCanBeConsumed(false);
            minecraftConfiguration.setCanBeResolved(false);

            Configuration deobfuscatedConfiguration = configurations.maybeCreate(deobfuscatedConfigurationName);
            deobfuscatedConfiguration.setVisible(false);
            deobfuscatedConfiguration.setDescription("Deobfuscated dependencies for " + sourceSetName + ".");
            deobfuscatedConfiguration.setCanBeConsumed(false);
            deobfuscatedConfiguration.setCanBeResolved(false);

            implementationSourceSetConfiguration.extendsFrom(minecraftConfiguration);
            implementationSourceSetConfiguration.extendsFrom(deobfuscatedConfiguration);
        });
    }

    /**
     * Validates that the configurations used to set minecraft dependencies only contain one minecraft version instance.
     * Filters out all configurations with not at exactly one minecraft dependency. If more the one is found an exception is thrown.
     *
     * This forces all minecraft configurations to have, either no minecraft dependency, or all the same minecraft dependency.
     * No other configurations are valid.
     *
     * @param configurationsToCheck Configurations to check
     * @return All configurations with minecraft dependencies.
     * @throws IllegalStateException when either no configurations contain minecraft, or two different versions of minecraft are found.
     */
    private List<Configuration> validateMinecraftDependencies(List<Configuration> configurationsToCheck, Logger logger) throws IllegalStateException
    {
        if (configurationsToCheck.isEmpty())
            throw new IllegalStateException("No configuration with a minecraft dependency found");

        final List<Configuration> validConfigurations = new ArrayList<>();
        String validInformation = "";
        for (final Configuration configuration:
            configurationsToCheck)
        {
            final String information = this.getMinecraftDependencyInformationFromConfiguration(configuration);
            //This configuration (and with that its sourceSet) does not depend on minecraft skip it.
            if (information.isEmpty())
            {
                continue;
            }

            if (validInformation.isEmpty())
            {
                validInformation = information;
            }
            else if (!validInformation.equals(information))
            {
                throw new IllegalStateException(String.format(
                  "Found multiple minecraft dependencies, with different dependency information. This is not allowed. The entire project has to depend on one minecraft version: %s",
                  validInformation));
            }

            validConfigurations.add(configuration);
        }

        if (validConfigurations.isEmpty())
            throw new IllegalStateException("At least one configuration has to depend on minecraft.");

        return validConfigurations;
    }

    /**
     * Extracts the minecraft dependency information string from the configuration.
     * If no dependency is found an empty string is returned.
     *
     * There needs to be exactly one, otherwise an exception is thrown.
     * The minecraft dependency needs to be a maven dependency.
     *
     * @param configuration The configuration to extract the information from.
     * @return A string containing the maven dependency information for minecraft, or an empty one, if no dependency is found.
     * @throws IllegalStateException thrown when the configuration is not set up correctly.
     */
    private String getMinecraftDependencyInformationFromConfiguration(Configuration configuration) throws IllegalStateException
    {
        if (configuration.getDependencies().size() == 0)
            return "";

        if (configuration.getDependencies().size() > 1)
            throw new IllegalStateException(String.format("Configuration: %s contains more then one minecraft dependency", configuration.getName()));

        final Dependency minecraftDependency = new ArrayList<>(configuration.getDependencies()).get(0);
        if (!(minecraftDependency instanceof ExternalModuleDependency))
            throw new IllegalArgumentException("Minecraft dependency: " + minecraftDependency.toString() + " of configuration: " + configuration.getName() + " must be a maven dependency.");

        return String.format("%s:%s:%s", minecraftDependency.getGroup(), minecraftDependency.getName(), minecraftDependency.getVersion());
    }

    /**
     * Generates a minecraft user repo from the project and valid configurations.
     *
     * @param project The project to generate for.
     * @param extension The extensions with the project information.
     * @param validMinecraftConfigurations The valid configurations.
     * @return The minecraft user repo.
     */
    private MinecraftUserRepo generateMinecraftUserRepo(Project project, UserDevExtension extension, List<Configuration> validMinecraftConfigurations)
    {
        //We can do this since the configurations have been validated.
        final Dependency dependency = new ArrayList<>(validMinecraftConfigurations.get(0).getDependencies()).get(0);
        return new MinecraftUserRepo(project, dependency.getGroup(), dependency.getName(), dependency.getVersion(), extension.getAccessTransformers(), extension.getMappings());
    }

    /**
     * Generates a special dependency that represents a deobfuscated minecraft.
     *
     * @param repo The magic maven repo that performs the deobfuscation.
     * @param logger The logger for the project.
     * @return The magic dependency representing a deobfuscated minecraft.
     */
    private ExternalModuleDependency generateDeobfuscatedMinecraftDependency(MinecraftUserRepo repo, Logger logger)
    {
        final String minecraftDeobfuscatedDependencyString = repo.getDependencyString();

        //Log to the logger which MC has been selected for debugging purposes.
        //Required by lex to see which version is being loaded.
        logger.lifecycle(String.format("Creating new deobfuscated minecraft dependency: %s", minecraftDeobfuscatedDependencyString));

        return (ExternalModuleDependency) repo.getProject().getDependencies().create(minecraftDeobfuscatedDependencyString);
    }
}

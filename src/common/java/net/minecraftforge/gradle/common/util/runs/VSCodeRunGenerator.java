package net.minecraftforge.gradle.common.util.runs;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.util.Map;

public class VSCodeRunGenerator extends RunConfigGenerator.JsonConfigurationBuilder
{
    @Override
    @Nonnull
    protected JsonObject createRunConfiguration(@Nonnull final Project project, @Nonnull final RunConfig runConfig, @Nonnull final String props) {
        JsonObject config = new JsonObject();
        config.addProperty("type", "java");
        config.addProperty("name", runConfig.getTaskName());
        config.addProperty("request", "launch");
        config.addProperty("mainClass", runConfig.getMain());
        config.addProperty("projectName", project.getName());
        config.addProperty("cwd", replaceRootDirBy(project, runConfig.getWorkingDirectory(), "${workspaceFolder}"));
        config.addProperty("vmArgs", props);
        config.addProperty("args", String.join(" ", runConfig.getArgs()));
        JsonObject env = new JsonObject();
        Map<String, String> environment = Maps.newHashMap(runConfig.getEnvironment());
        environment.computeIfAbsent("MOD_CLASSES", (key) ->
                replaceRootDirBy(project, EclipseRunGenerator.mapModClassesToEclipse(project, runConfig), "${workspaceFolder}"));
        environment.compute("nativesDirectory", (key, value) ->
                replaceRootDirBy(project, value, "${workspaceFolder}"));
        environment.forEach(env::addProperty);
        config.add("env", env);
        return config;
    }
}

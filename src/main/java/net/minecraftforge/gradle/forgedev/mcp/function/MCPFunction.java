package net.minecraftforge.gradle.forgedev.mcp.function;

import org.gradle.api.Project;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.File;
import java.util.Map;

public interface MCPFunction {

    File execute(Project project, Map<String, String> arguments) throws Exception;

    default void loadData(JsonObject data) throws Exception {
    }

}

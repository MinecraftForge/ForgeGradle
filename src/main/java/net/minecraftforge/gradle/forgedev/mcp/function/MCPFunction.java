package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;
import org.gradle.internal.impldep.com.google.gson.JsonObject;

import java.io.File;

public interface MCPFunction {

    File execute(MCPEnvironment environment) throws Exception;

    default void loadData(JsonObject data) throws Exception {
    }

}

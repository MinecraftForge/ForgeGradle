package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.zip.ZipFile;

public interface MCPFunction {

    default void loadData(JsonObject data) throws Exception {
    }

    default void initialize(MCPEnvironment environment, ZipFile zip) throws Exception {
    }

    File execute(MCPEnvironment environment) throws Exception;

    default void cleanup(MCPEnvironment environment) {
    }

}

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import com.google.gson.JsonObject;

import java.util.zip.ZipFile;

public interface MCPFunctionOverlay {

    default void loadData(JsonObject data) throws Exception {
    }

    default void initialize(MCPEnvironment environment, ZipFile zip) throws Exception {
    }

    default void onExecuted(MCPEnvironment environment) throws Exception {
    }

}

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import java.io.File;
import java.util.Map;
import java.util.zip.ZipFile;

public interface MCPFunction {

    default void loadData(Map<String, String> data) {
    }

    default void initialize(MCPEnvironment environment, ZipFile zip) throws Exception {
    }

    File execute(MCPEnvironment environment) throws Exception;

    default void cleanup(MCPEnvironment environment) {
    }

    default void addInputs(HashStore cache, String prefix) {
    }

}

package net.minecraftforge.gradle.forgedev.mcp.function;

import net.minecraftforge.gradle.forgedev.mcp.util.MCPEnvironment;

import java.io.File;

public class NullFunction implements MCPFunction {

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        return environment.getFile("out");
    }

}

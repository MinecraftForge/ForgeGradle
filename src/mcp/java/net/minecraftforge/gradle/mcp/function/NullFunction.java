package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

import java.io.File;

public class NullFunction implements MCPFunction {

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        return environment.getFile("out");
    }

}

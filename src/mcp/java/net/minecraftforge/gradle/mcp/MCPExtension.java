package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;

import javax.inject.Inject;

public class MCPExtension {

    public Object config;
    public String pipeline;


    @Inject
    public MCPExtension(Project project) {
    }

}

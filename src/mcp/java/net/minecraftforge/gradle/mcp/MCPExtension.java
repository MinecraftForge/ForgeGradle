package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;

import net.minecraftforge.gradle.common.util.Artifact;

import javax.inject.Inject;

public class MCPExtension {

    private Artifact config;
    public String pipeline;


    @Inject
    public MCPExtension(Project project) {
    }

    public Artifact getConfig() {
        return config;
    }

    public void setConfig(String value) {
        if (value.indexOf(':') != -1) // Full artifact
            config = Artifact.from(value);
        else
            config = Artifact.from("de.oceanlabs.mcp:mcp_config:" + value + "@zip");
    }

}

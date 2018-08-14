package net.minecraftforge.gradle.patcher;

import org.gradle.api.Project;

import net.minecraftforge.gradle.mcp.task.DownloadMCPMappingsTask;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

public class PatcherExtension {

    public Project parent;
    public File patchedSrc;
    public File patches;
    private Object mappings;

    @Inject
    public PatcherExtension(Project project) {
    }

    public Object getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> map) { //Whats the difference between the action and map?
        mappings = DownloadMCPMappingsTask.getDefault(map.get("channel"), map.get("version")); //mappings channel: 'snapshot', version: '20180101' Will append current MC version if none is specified in Version
    }

    public void setMappings(Object obj) {
        if (obj instanceof String || //Custom full artifact
            obj instanceof File  ) { //Custom zip file
            mappings = obj;
        } else {
            throw new IllegalArgumentException("Mappings must be file, string, or map");
        }
    }

}

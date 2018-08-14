package net.minecraftforge.gradle.patcher;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

public class PatcherExtension {

    public File cleanSrc;
    public File patchedSrc;
    public File patches;
    private Object mappings;

    @Inject
    public PatcherExtension(Project project) {
    }

    public Object getMappings() {
        return mappings;
    }

    // mappings channel: 'snapshot', version: '20180101'
    public void mappings(Map<String, String> map) {
        String channel = map.get("channel");
        String version = map.get("version");
        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify mappings channel and version");
        }

        if (!version.contains("-")) version = version + "-+";
        setMappings("de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip");
    }

    public void setMappings(Object obj) {
        if (obj instanceof String || //Custom full artifact
                obj instanceof File) { //Custom zip file
            mappings = obj;
        } else {
            throw new IllegalArgumentException("Mappings must be file, string, or map");
        }
    }

}

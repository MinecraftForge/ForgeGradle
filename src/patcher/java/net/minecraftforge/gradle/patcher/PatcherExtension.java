package net.minecraftforge.gradle.patcher;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PatcherExtension {

    public File cleanSrc;
    public File patchedSrc;
    public File patches;
    public String mcVersion;
    private Object mappings;
    private List<Object> extraMappings = new ArrayList<>();
    private List<Object> extraExcs = new ArrayList<>();

    @Inject
    public PatcherExtension(Project project) {
    }

    public Object getMappings() {
        return mappings;
    }

    public List<Object> getExtraMappings() {
        return extraMappings;
    }

    public List<Object> getExtraExcs() {
        return extraExcs;
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

    public void extraMapping(Object mapping) {
        if (mapping instanceof String || mapping instanceof File) {
            extraMappings.add(mapping);
        } else {
            throw new IllegalArgumentException("Extra mappings must be a file or a string!");
        }
    }

    public void extraExc(Object exc) {
        extraExcs.add(exc); // TODO: Type check!
    }

}

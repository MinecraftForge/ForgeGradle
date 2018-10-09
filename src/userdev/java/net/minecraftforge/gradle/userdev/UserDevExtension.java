package net.minecraftforge.gradle.userdev;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserDevExtension {
    private String mappings;
    private List<File> accessTransformers = new ArrayList<>();

    @Inject
    public UserDevExtension(Project project) {
    }

    public String getMappings() {
        return mappings;
    }

    public List<File> getAccessTransformers() {
        return accessTransformers;
    }

    // mappings channel: 'snapshot', version: '20180101'
    public void mappings(Map<String, String> map) {
        String channel = map.get("channel");
        String version = map.get("version");
        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify mappings channel and version");
        }

        //setMappings("de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip");
        setMappings(channel + '_' + version);
    }

    public void setMappings(String value) {
        mappings = value;
    }

    public void setAccessTransformers(List<File> files) {
         this.accessTransformers.clear();
         this.accessTransformers.addAll(files);
    }

    public void setAccessTransformer(File file) {
        this.accessTransformers.add(file);
    }
}

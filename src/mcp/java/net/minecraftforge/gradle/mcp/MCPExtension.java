package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MCPExtension {

    public Object config;
    public String pipeline;

    private Object mappings;

    private final List<Object> accessTransformers = new ArrayList<>();

    @Inject
    public MCPExtension(Project project) {
    }

    public void setMappings(Object obj) {
        if (obj instanceof String || //Custom full artifact
                obj instanceof File) { //Custom zip file
            mappings = obj;
        } else {
            throw new IllegalArgumentException("Mappings must be file, string, or map");
        }
    }

    public void addAccessTransformer(String transformer) {
        this.accessTransformers.add(transformer);
    }

    public void addAccessTransformer(Supplier<String> transformer) {
        this.accessTransformers.add(transformer);
    }

    public List<String> getAccessTransformers() {
        return accessTransformers.stream().map(MCPExtension::getTransformer).collect(Collectors.toList());
    }

    private static String getTransformer(Object o) {
        if (o instanceof String) {
            return (String) o;
        } else if (o instanceof Supplier) {
            return ((Supplier<String>) o).get();
        }
        throw new IllegalArgumentException("Invalid access transformer: " + o);
    }

}

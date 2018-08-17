package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MCPExtension {

    public Object config;
    public String pipeline;

    private final List<Object> accessTransformers = new ArrayList<>();

    @Inject
    public MCPExtension(Project project) {
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

    @SuppressWarnings("unchecked")
    private static String getTransformer(Object o) {
        if (o instanceof String) {
            return (String) o;
        } else if (o instanceof Supplier) {
            return ((Supplier<String>) o).get();
        }
        throw new IllegalArgumentException("Invalid access transformer: " + o);
    }

}

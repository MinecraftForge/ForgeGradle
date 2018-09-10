package net.minecraftforge.gradle.patcher;

import org.gradle.api.Project;

import groovy.lang.Closure;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatcherExtension {

    public Project parent;
    public File cleanSrc;
    public File patchedSrc;
    public File patches;
    public String mcVersion;
    public boolean srgPatches = true;
    private Object mappings;
    private List<Object> extraMappings = new ArrayList<>();
    private List<Object> extraExcs = new ArrayList<>();
    private List<File> accessTransformers = new ArrayList<>();
    private RunConfig clientRun = new RunConfig();
    private RunConfig serverRun = new RunConfig();

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

    void copyFrom(PatcherExtension other) {
        if (mappings == null) {
            this.setMappings(other.getMappings());
        }
        if (this.mcVersion == null) {
            this.mcVersion = other.mcVersion;
        }
    }

    public void setAccessTransformers(List<File> files) {
         this.accessTransformers.clear();
         this.accessTransformers.addAll(files);
    }

    public void setAccessTransformer(File file) {
        this.accessTransformers.add(file);
    }

    public void setClientRun(Closure<? super RunConfig> action) {
        action.setResolveStrategy(Closure.DELEGATE_FIRST);
        action.setDelegate(clientRun);
        action.call();
    }

    public RunConfig getClientRun() {
        return clientRun;
    }

    public void setServerRun(Closure<? super RunConfig> action) {
        action.setResolveStrategy(Closure.DELEGATE_FIRST);
        action.setDelegate(serverRun);
        action.call();
    }

    public RunConfig getServerRun() {
        return serverRun;
    }

    public static class RunConfig {
        private String main;
        private Map<String, String> env = new HashMap<>();
        private Map<String, String> props = new HashMap<>();

        public void setEnvironment(Map<String, Object> map) {
            map.forEach((k,v) -> env.put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
        }
        public void environment(String key, String value) {
            env.put(key, value);
        }
        public void environment(String key, File value) {
            env.put(key, value.getAbsolutePath());
        }
        public Map<String, String> getEnvironment() {
            return env;
        }

        public void setMain(String value) {
            this.main = value;
        }
        public String getMain() {
            return this.main;
        }

        public void setProperties(Map<String, Object> map) {
            map.forEach((k,v) -> props.put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
        }
        public void property(String key, String value) {
            props.put(key, value);
        }
        public void property(String key, File value) {
            props.put(key, value.getAbsolutePath());
        }
        public Map<String, String> getProperties() {
            return props;
        }
    }
}

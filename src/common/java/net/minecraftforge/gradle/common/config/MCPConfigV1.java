package net.minecraftforge.gradle.common.config;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MCPConfigV1 extends Config {
    public String version;
    public Map<String, Object> data;
    public Map<String, List<String>> libraries;

    @SuppressWarnings("unchecked")
    public String getData(String... path) {
        Map<String, Object> level = data;
        for (String part : path) {
            if (!level.containsKey(part))
                return null;
            Object val = level.get(part);
            if (val instanceof String)
                return (String)val;
            if (val instanceof Map)
                level = (Map<String, Object>)val;
        }
        return null;
    }

    public List<String> getLibraries(String side) {
        List<String> ret = libraries == null ? null : libraries.get(side);
        return ret == null ? Collections.emptyList() : ret;
    }
}

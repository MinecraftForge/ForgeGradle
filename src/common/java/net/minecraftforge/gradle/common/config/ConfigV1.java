package net.minecraftforge.gradle.common.config;

import java.util.Map;

public class ConfigV1 extends Config {
    public String version;
    public Map<String, Object> data;

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
}

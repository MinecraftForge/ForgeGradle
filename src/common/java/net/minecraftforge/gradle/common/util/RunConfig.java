/*
 * ForgeGradle
 * Copyright (C) 2018.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class RunConfig {
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
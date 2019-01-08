/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.tasks.SourceSet;

import com.google.common.base.Joiner;

import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;

public class RunConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private String main;
    private List<String> args;
    private Map<String, String> env;
    private Map<String, String> props;
    private boolean singleInstance = false;
    private String ideaModule;
    private String workDir;
    private List<SourceSet> sources;

    public void environment(Map<String, Object> map) {
        this.setEnvironment(map);
    }
    public void setEnvironment(Map<String, Object> map) {
        map.forEach((k,v) -> getEnvironment().put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
    }
    public void environment(String key, String value) {
        getEnvironment().put(key, value);
    }
    public void environment(String key, File value) {
        getEnvironment().put(key, value.getAbsolutePath());
    }
    public Map<String, String> getEnvironment() {
        if (env == null)
            env = new HashMap<>();
        return env;
    }

    public void main(String value) {
        this.setMain(value);
    }
    public void setMain(String value) {
        this.main = value;
    }
    public String getMain() {
        return this.main;
    }

    public void arg(String value) {
        getArgs().add(value);
    }
    public void setArgs(List<String> values) {
        getArgs().addAll(values);
    }
    public List<String> getArgs() {
        if (args == null)
            args = new ArrayList<>();
        return args;
    }

    public void singleInstance(boolean value) {
        this.setSingleInstance(value);
    }
    public void setSingleInstance(boolean singleInstance) {
        this.singleInstance = singleInstance;
    }
    public boolean isSingleInstance() {
        return singleInstance;
    }

    public void properties(Map<String, Object> map) {
        this.setProperties(map);
    }
    public void setProperties(Map<String, Object> map) {
        map.forEach((k,v) -> getProperties().put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
    }
    public void property(String key, String value) {
        getProperties().put(key, value);
    }
    public void property(String key, File value) {
        getProperties().put(key, value.getAbsolutePath());
    }
    public Map<String, String> getProperties() {
        if (props == null)
            props = new HashMap<>();
        return props;
    }

    public void ideaModule(String value) {
        this.setIdeaModule(value);
    }
    public void setIdeaModule(String value) {
        this.ideaModule = value;
    }
    public String getIdeaModule() {
        return ideaModule;
    }

    public void workingDirectory(String value) {
        this.setWorkingDirectory(value);
    }
    public void setWorkingDirectory(String value) {
        this.workDir = value;
    }
    public String getWorkingDirectory() {
        return workDir == null ? "." : workDir;
    }

    public void source(SourceSet value) {
        this.getSources().add(value);
    }
    public void setSources(List<SourceSet> value) {
        this.sources = value;
    }
    public List<SourceSet> getSources() {
        if (this.sources == null)
            this.sources = new ArrayList<>();
        return this.sources;
    }

    private List<File> getSourceDirs() {
        final List<File> ret = new ArrayList<>();
        getSources().forEach(set -> {
            ret.add(set.getOutput().getResourcesDir());
            ret.addAll(set.getOutput().getClassesDirs().getFiles());
            ret.addAll(set.getOutput().getDirs().getFiles());
        });
        return ret;
    }

    public void merge(RunConfig other, boolean overwrite, Map<String, String> vars) {
        vars.put("source_roots", Joiner.on(File.pathSeparator).join(getSourceDirs()));

        this.singleInstance = other.singleInstance; // This always overwrite cuz there is no way to tell if it's set
        if (overwrite) {
            this.args = other.args == null ? this.args : other.args;
            this.main = other.main == null ? this.main : other.main;
            this.workDir = other.workDir == null ? this.workDir : other.workDir;
            this.ideaModule = other.ideaModule == null ? this.ideaModule : other.ideaModule;

            if (other.env != null) {
                other.env.forEach((k,v) -> getEnvironment().put(k, replace(vars, v)));
            }

            if (other.props != null) {
                other.props.forEach((k,v) -> getProperties().put(k, replace(vars, v)));
            }
        } else {
            this.args = other.args == null || this.args != null ? this.args : other.args;
            this.main = other.main == null || this.main != null ? this.main : other.main;
            this.workDir = other.workDir == null || this.workDir != null ? this.workDir : other.workDir;
            this.ideaModule = other.ideaModule == null || this.ideaModule != null ? this.ideaModule : other.ideaModule;

            if (other.env != null) {
                other.env.forEach((k,v) -> getEnvironment().putIfAbsent(k, replace(vars, v)));
            }

            if (other.props != null) {
                other.props.forEach((k,v) -> getProperties().putIfAbsent(k, replace(vars, v)));
            }
        }
    }

    private String replace(Map<String, String> vars, String value) {
        if (value == null || value.length() <= 2 || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}')
            return value;
        String key = value.substring(1, value.length() - 1);
        String resolved = vars.get(key);
        return resolved == null ? value : resolved;
    }

    public static class Container {
        private Map<String, RunConfig> runs = new HashMap<>();

        // This doesn't work, no idea why... so users must use =
        public void methodMissing(String name, Object value) {
            propertyMissing(name, value);
        }

        public Object propertyMissing(String name) {
            if (!this.runs.containsKey(name))
                throw new MissingPropertyException(name);
            return this.runs.get(name);
        }

        public void propertyMissing(String name, Object value) {
            if (!(value instanceof Closure))
                throw new IllegalArgumentException("Argument must be Closure");

            @SuppressWarnings("unchecked")
            Closure<? extends RunConfig> closure = (Closure<? extends RunConfig>)value;
            RunConfig run = new RunConfig();
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(run);
            closure.call();
            this.runs.put(name, run);
        }

        public Map<String, RunConfig> getRuns() {
            return runs;
        }
    }
}

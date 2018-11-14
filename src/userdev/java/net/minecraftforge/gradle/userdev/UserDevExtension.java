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

package net.minecraftforge.gradle.userdev;

import org.gradle.api.Project;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.util.RunConfig;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserDevExtension {
    private String mappings;
    private List<File> accessTransformers = new ArrayList<>();
    private RunConfig clientRun = new RunConfig();
    private RunConfig serverRun = new RunConfig();

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
}

/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.MinecraftRepo;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Internal Use Only
 * Non-Public API, Can be changed at any time.
 */
public class MCPFunctionFactory {
    /**
     * Internal Use Only
     * Non-Public API, Can be changed at any time.
     */
    @Deprecated
    @Nullable
    public static MCPFunction createBuiltIn(String type, int spec) {
        switch (type) {
            case "downloadManifest":
                return new DownloadFileFunction("manifest.json", MinecraftRepo.MANIFEST_URL);
            case "downloadJson":
                return new DownloadVersionJSONFunction();
            case "downloadClient":
                return new DownloadCoreFunction("client", "jar");
            case "downloadServer":
                return new DownloadCoreFunction("server", "jar");
            case "strip":
                return new StripJarFunction();
            case "listLibraries":
                return new ListLibrariesFunction(spec);
            case "inject":
                return new InjectFunction();
            case "patch":
                return new PatchFunction();
        }
        if (spec >= 2) {
            switch (type) {
                case "downloadClientMappings":
                    return new DownloadCoreFunction("client_mappings", "txt");
                case "downloadServerMappings":
                    return new DownloadCoreFunction("server_mappings", "txt");
            }
        }
        return null;
    }

    /**
     * Internal Use Only
     * Non-Public API, Can be changed at any time.
     */
    @Deprecated
    public static MCPFunction createAT(Project project, List<File> files, Collection<String> data) {
        AccessTransformerFunction ret = new AccessTransformerFunction(project, files);
        data.forEach(ret::addTransformer);
        return ret;
    }

    /**
     * Internal Use Only
     * Non-Public API, Can be changed at any time.
     */
    @Deprecated
    public static MCPFunction createSAS(Project project, List<File> files, Collection<String> data) {
        SideAnnotationStripperFunction ret = new SideAnnotationStripperFunction(project, files);
        data.forEach(ret::addData);
        return ret;
    }

    /**
     * Internal Use Only
     * Non-Public API, Can be changed at any time.
     */
    @Deprecated
    public static MCPFunction createExecute(File jar, List<String> jvmArgs, List<String> runArgs, @Nullable Integer javaVersion) {
        return new ExecuteFunction(jar,
            jvmArgs.toArray(new String[jvmArgs.size()]),
            runArgs.toArray(new String[runArgs.size()]),
            Collections.emptyMap(),
            javaVersion);
    }
}

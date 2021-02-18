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

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URL;
import java.util.function.Function;

class DownloadFileFunction implements MCPFunction {

    private final Function<MCPEnvironment, String> outputGetter;
    private final Function<MCPEnvironment, DownloadInfo> downloadGetter;

    public DownloadFileFunction(Function<MCPEnvironment, String> outputGetter, Function<MCPEnvironment, DownloadInfo> downloadGetter) {
        this.outputGetter = outputGetter;
        this.downloadGetter = downloadGetter;
    }

    public DownloadFileFunction(String defaultOutput, String url) {
        this(env -> defaultOutput, env -> new DownloadInfo(url, null, "unknown", null, null));
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File output = (File)environment.getArguments().computeIfAbsent("output", k -> environment.getFile(outputGetter.apply(environment)));
        File download = !output.exists() ? output : environment.getFile(output.getAbsolutePath() + ".new");

        Utils.delete(download); // This file should never exist, but abrupt termination of the process may leave it behind

        DownloadInfo info = downloadGetter.apply(environment);
        if (info.hash != null && output.exists() && HashFunction.SHA1.hash(output).equalsIgnoreCase(info.hash)) {
            return output; // If the hash matches, don't download again
        }
        // Check if file exists in local installer cache
        if (info.type.equals("jar") && info.side.equals("client")) {
            File localPath = new File(Utils.getMCDir() + File.separator + "versions" + File.separator + info.version + File.separator + info.version + ".jar");
            if (localPath.exists() && HashFunction.SHA1.hash(localPath).equalsIgnoreCase(info.hash)) {
                FileUtils.copyFile(localPath, download);
            } else {
                FileUtils.copyURLToFile(new URL(info.url), download);
            }
        } else {
            FileUtils.copyURLToFile(new URL(info.url), download);
        }

        if (output != download) {
            if (FileUtils.contentEquals(output, download)) {
                download.delete();
            } else {
                output.delete();
                download.renameTo(output);
            }
        }

        return output;
    }

    static class DownloadInfo {

        private final String url;
        private final String hash;
        private final String type;
        private final String version;
        private final String side;

        public DownloadInfo(String url, @Nullable String hash, String type, @Nullable String version, @Nullable String side) {
            this.url = url;
            this.hash = hash;
            this.type = type;
            this.version = version;
            this.side = side;

        }

    }

}

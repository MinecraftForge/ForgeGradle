package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.FileUtils;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.net.URL;
import java.util.function.Function;

public abstract class AbstractFileDownloadFunction implements MCPFunction {

    private final Function<MCPEnvironment, String> outputGetter;
    private final Function<MCPEnvironment, DownloadInfo> downloadGetter;

    public AbstractFileDownloadFunction(Function<MCPEnvironment, String> outputGetter, Function<MCPEnvironment, DownloadInfo> downloadGetter) {
        this.outputGetter = outputGetter;
        this.downloadGetter = downloadGetter;
    }

    public AbstractFileDownloadFunction(String defaultOutput, String url) {
        this(env -> defaultOutput, env -> new DownloadInfo(url, null));
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File output = (File)environment.getArguments().computeIfAbsent("output", k -> environment.getFile(outputGetter.apply(environment)));
        File download = !output.exists() ? output : environment.getFile(output.getAbsolutePath() + ".new");

        Utils.delete(download); // This file should never exist, but abrupt termination of the process may leave it behind

        DownloadInfo info = downloadGetter.apply(environment);
        if (info.hash != null && output.exists() && HashUtil.sha1(output).equals(info.hash)) {
            return output; // If the hash matches, don't download again
        }
        FileUtils.copyURLToFile(new URL(info.url), download);

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
        private final HashValue hash;

        public DownloadInfo(String url, HashValue hash) {
            this.url = url;
            this.hash = hash;
        }

    }

}

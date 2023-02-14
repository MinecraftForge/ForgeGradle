/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

import org.gradle.api.Project;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

class SideAnnotationStripperFunction extends ExecuteFunction {
    private List<File> files;
    private String data;

    public SideAnnotationStripperFunction(Project mcp, List<File> files) {
        super(getJar(mcp), new String[0], getArguments(files), new HashMap<>());
        this.files = files;
    }

    private static File getJar(Project mcp) { //TODO: configurable version?
        return MavenArtifactDownloader.gradle(mcp, Utils.SIDESTRIPPER, false);
    }

    private static String[] getArguments(List<File> files) {
        List<String> args = new ArrayList<>();
        args.add("--strip");
        args.add("--input");
        args.add("{input}");
        args.add("--output");
        args.add("{output}");
        files.forEach(f -> {
            args.add("--data");
            args.add(f.getAbsolutePath());
        });
        return args.toArray(new String[args.size()]);
    }

    @Override
    public File execute(MCPEnvironment env) throws IOException, InterruptedException, ExecutionException {
        if (data != null) {
            File tmp = env.getFile("string_data.sas").getAbsoluteFile();
            if (!tmp.getParentFile().exists())
                tmp.getParentFile().mkdirs();
            Files.write(tmp.toPath(), data.getBytes());
            List<String> args = new ArrayList<>(Arrays.asList(runArgs));
            args.add("--data");
            args.add(tmp.getAbsolutePath());
            runArgs = args.toArray(new String[args.size()]);
        }
        return super.execute(env);
    }


    public void addData(String data) {
        if (this.data == null) this.data = data;
        else this.data += "\n#============================================================\n" + data;
    }

    @Override
    protected void addInputs(HashStore cache) {
        cache.add(files);
        if (data != null)
            cache.add("data", data);
    }

    @Override
    public void addInputs(HashStore cache, String prefix) { //Called by setupMain before executed
        cache.add(prefix + "args", String.join(" ", runArgs));
        cache.add(prefix + "jvmargs", String.join(" ", runArgs));
        cache.add(files);
        if (data != null)
            cache.add(prefix + "data", data);
        try {
            cache.add(prefix + "jar", jar);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

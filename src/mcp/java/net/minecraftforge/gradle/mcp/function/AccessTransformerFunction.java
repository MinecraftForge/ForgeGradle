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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class AccessTransformerFunction extends ExecuteFunction {
    private List<File> files;
    private String transformers;

    public AccessTransformerFunction(Project mcp, List<File> files) {
        super(getJar(mcp), new String[0], getArguments(files), new HashMap<>());
        this.loadData(Collections.emptyMap());
        this.files = files;
    }

    private static File getJar(Project mcp) { //TODO: configurable version?
        return MavenArtifactDownloader.gradle(mcp, Utils.ACCESSTRANSFORMER, false);
    }

    private static String[] getArguments(List<File> files) {
        List<String> args = new ArrayList<>();
        args.add("--inJar");
        args.add("{input}");
        args.add("--outJar");
        args.add("{output}");
        files.forEach(f -> {
            args.add("--atFile");
            args.add(f.getAbsolutePath());
        });
        return args.toArray(new String[args.size()]);
    }

    @Override
    public File execute(MCPEnvironment env) throws IOException, InterruptedException, ExecutionException {

        if (transformers != null) {
            File tmp = File.createTempFile("FG_ats_", ".cfg");
            tmp.deleteOnExit();
            Files.write(tmp.toPath(), transformers.getBytes());
            List<String> args = new ArrayList<>(Arrays.asList(runArgs));
            args.add("--atFile");
            args.add(tmp.getAbsolutePath());
            runArgs = args.toArray(new String[args.size()]);
        }
        return super.execute(env);
    }


    public void addTransformer(String data) {
        if (transformers == null) transformers = data;
        else transformers += "\n#============================================================\n" + data;
    }

    @Override
    protected void addInputs(HashStore cache) {
        cache.add(files);
        if (transformers != null)
            cache.add("transformers", transformers);
    }

    @Override
    public void addInputs(HashStore cache, String prefix) { //Called by setupMain before executed
        cache.add(prefix + "args", String.join(" ", runArgs));
        cache.add(prefix + "jvmargs", String.join(" ", runArgs));
        cache.add(files);
        if (transformers != null)
            cache.add(prefix + "transformers", transformers);
        try {
            cache.add(prefix + "jar", jar);
        } catch (Exception e) {
            e.printStackTrace();
        }
        addInputs(cache);
    }
}

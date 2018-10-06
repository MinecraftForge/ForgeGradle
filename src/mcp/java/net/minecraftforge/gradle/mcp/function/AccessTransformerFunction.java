package net.minecraftforge.gradle.mcp.function;

import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import org.gradle.api.Project;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AccessTransformerFunction extends ExecuteFunction {
    private List<File> files;

    public AccessTransformerFunction(Project mcp, List<File> files) {
        super(getJar(mcp), new String[0], getArguments(files), new HashMap<>());
        this.loadData(new JsonObject());
        this.files = files;
    }

    private static File getJar(Project mcp) { //TODO: configurable version?
        return MavenArtifactDownloader.download(mcp, "net.minecraftforge:accesstransformers:0.10.0-rc.4.+:fatjar").iterator().next();
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
        return args.toArray(new String[0]);
    }

    @Override
    protected void addInputs(HashStore cache) {
        cache.add(files);
    }

    @Override
    public void addInputs(HashStore cache, String prefix) { //Called by setupMain before executed
        cache.add(prefix + "args", String.join(" ", runArgs));
        cache.add(prefix + "jvmargs", String.join(" ", runArgs));
        try {
            cache.add(prefix + "jar", jar);
        } catch (Exception e) {
            e.printStackTrace();
        }
        addInputs(cache);
    }
}

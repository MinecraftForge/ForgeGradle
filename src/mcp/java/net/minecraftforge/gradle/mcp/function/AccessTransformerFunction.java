package net.minecraftforge.gradle.mcp.function;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.gradle.api.Project;

import com.google.gson.JsonObject;

import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.mcp.task.ValidateMCPConfigTask;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

public class AccessTransformerFunction extends ExecuteFunction {
    private List<File> files;

    public AccessTransformerFunction(Project mcp, List<File> files) {
        super(getJar(mcp), new String[0], getArguments(files), new HashMap<>());
        this.loadData(new JsonObject());
        this.files = files;
    }

    private static CompletableFuture<File> getJar(Project mcp) {
        MCPConfig cfg  = ((ValidateMCPConfigTask)mcp.getTasks().getByName("validateConfig")).processed;
        CompletableFuture<File> jar = new CompletableFuture<>();
        cfg.dependencies.put("net.minecraftforge:accesstransformers:0.10.0-rc.4.10+:fatjar", jar); //TODO: configurable version?
        return jar;
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
    protected void addInputs(HashStore cache) {
        cache.add(files);
    }

    @Override
    public void addInputs(HashStore cache, String prefix) { //Called by setupMain before executed
        cache.add(prefix + "args", String.join(" ", runArgs));
        cache.add(prefix + "jvmargs", String.join(" ", runArgs));
        try {
            cache.add(prefix + "jar", jar.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        addInputs(cache);
    }
}

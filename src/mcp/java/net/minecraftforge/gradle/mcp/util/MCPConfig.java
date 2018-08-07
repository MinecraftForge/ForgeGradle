package net.minecraftforge.gradle.mcp.util;

import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionOverlay;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class MCPConfig {

    public String mcVersion;
    public File zipFile;
    public final Pipeline pipeline = new Pipeline();
    public final Libraries libraries = new Libraries();
    public final Map<String, CompletableFuture<File>> dependencies = new HashMap<>();

    public class Pipeline {

        public final List<Step> sharedSteps = new LinkedList<>();
        public final List<Step> srcSteps = new LinkedList<>();

        public void addShared(String name, String type, MCPFunction function, MCPFunctionOverlay overlay, Map<String, String> arguments) {
            sharedSteps.add(new Step(name, type, function, overlay, arguments));
        }

        public void addSrc(String name, String type, MCPFunction function, MCPFunctionOverlay overlay, Map<String, String> arguments) {
            srcSteps.add(new Step(name, type, function, overlay, arguments));
        }

        public class Step {

            public final String name;
            public final String type;
            public final MCPFunction function;
            public final MCPFunctionOverlay overlay;
            public final Map<String, String> arguments;

            private Step(String name, String type, MCPFunction function, MCPFunctionOverlay overlay, Map<String, String> arguments) {
                this.name = name;
                this.type = type;
                this.function = function;
                this.overlay = overlay;
                this.arguments = arguments;
            }

        }

    }

    public class Libraries {

        public final Set<String> client = new HashSet<>();
        public final Set<String> server = new HashSet<>();
        public final Set<String> joined = new HashSet<>();

    }

}

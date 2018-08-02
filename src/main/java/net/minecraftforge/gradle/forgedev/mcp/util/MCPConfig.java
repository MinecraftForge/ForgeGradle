package net.minecraftforge.gradle.forgedev.mcp.util;

import net.minecraftforge.gradle.forgedev.mcp.function.MCPFunction;

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
    public final Pipeline pipeline = new Pipeline();
    public final Libraries libraries = new Libraries();
    public final Map<String, CompletableFuture<File>> dependencies = new HashMap<>();

    public class Pipeline {

        public final List<Step> sharedSteps = new LinkedList<>();
        public final List<Step> srcSteps = new LinkedList<>();

        public void addShared(String type, MCPFunction function, Map<String, String> arguments) {
            sharedSteps.add(new Step(type, function, arguments));
        }

        public void addSrc(String type, MCPFunction function, Map<String, String> arguments) {
            srcSteps.add(new Step(type, function, arguments));
        }

        public class Step {

            public final String type;
            public final MCPFunction function;
            public final Map<String, String> arguments;

            private Step(String type, MCPFunction function, Map<String, String> arguments) {
                this.type = type;
                this.function = function;
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

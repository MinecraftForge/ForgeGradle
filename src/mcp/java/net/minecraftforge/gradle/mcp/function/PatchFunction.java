package net.minecraftforge.gradle.mcp.function;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.cloudbees.diff.PatchException;
import com.google.gson.JsonObject;

import net.minecraftforge.gradle.common.diff.ContextualPatch;
import net.minecraftforge.gradle.common.diff.ContextualPatch.PatchReport;
import net.minecraftforge.gradle.common.diff.HunkReport;
import net.minecraftforge.gradle.common.diff.PatchFile;
import net.minecraftforge.gradle.common.diff.ZipContext;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;

public class PatchFunction implements MCPFunction {

    private String path;
    private Map<String, String> patches;

    @Override
    public  void loadData(JsonObject data) throws Exception {
        path = data.get("patches").getAsString();
    }

    @Override
    public  void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        patches = zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(path) && e.getName().endsWith(".patch"))
        .collect(Collectors.toMap(e -> e.getName().substring(path.length()), e -> {
            try {
                return IOUtils.toString(zip.getInputStream(e));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
    }


    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = new File(environment.getArguments().get("input"));
        File inHashFile = environment.getFile("lastinput.sha1");
        File output = environment.getFile("output.jar");

        if (environment.shouldSkipStep()) return output; //TODO: Should move this to a helper function?

        Map<String, String> inputs = new HashMap<>();
        inputs.put("input", HashFunction.SHA1.hash(input));
        patches.forEach((k,v) -> inputs.put(k, HashFunction.SHA1.hash(v)));

        // If this has already been computed, skip
        if (inHashFile.exists() && output.exists()) {
            Map<String, String> cache = Utils.loadHashMap(inHashFile);
            if (cache.equals(inputs)) {
                return output;
            }
        }

        // Store the hash of the input for future reference
        if (inHashFile.exists()) inHashFile.delete();
        if (output.exists()) output.delete();

        try (ZipFile zip = new ZipFile(input)) {
            ZipContext context = new ZipContext(zip);

            boolean success = true;
            for (Entry<String, String> entry : patches.entrySet()) {
                ContextualPatch patch = ContextualPatch.create(PatchFile.from(entry.getValue()), context);
                patch.setCanonialization(true, false); //We are applying MCP patches, Which can have access transformers, but not refactors.
                patch.setMaxFuzz(0); //They should also never fuzz.

                try {
                    environment.logger.info("Apply Patch: " + entry.getKey());
                    List<PatchReport> result = patch.patch(false);
                    for (int x = 0; x < result.size(); x++) {
                        PatchReport report = result.get(x);
                        if (!report.getStatus().isSuccess()) {
                            success = false;
                            for (int y = 0; y < report.hunkReports().size(); y++) {
                                HunkReport hunk = report.hunkReports().get(y);
                                if (hunk.hasFailed()) {
                                    if (hunk.failure == null) {
                                        environment.logger.error("  Hunk #" + hunk.hunkID + " Failed @" + hunk.index + " Fuzz: " + hunk.fuzz);
                                    } else {
                                        environment.logger.error("  Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.getMessage());
                                    }

                                }
                            }
                        }
                    }
                } catch (PatchException | IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (success) {
                context.save(output);
                Utils.saveHashMap(inHashFile, inputs);
            }
        }

        return output;
    }

    @Override
    public  void cleanup(MCPEnvironment environment) {
        this.patches.clear();
    }

}

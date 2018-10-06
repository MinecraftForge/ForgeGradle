package net.minecraftforge.gradle.mcp;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.artifact.SimpleArtifactIdentifier;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.gradle.api.Project;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;

import java.io.File;
import java.util.Collections;

public class MCPRepository {

    private static final String GROUP = "net.minecraft";
    private static final String NAME = "mcpgenerated";
    private static final String EXTENSION = "jar";

    private final Project project;
    private final File mcpConfig;

    public MCPRepository(Project project, File mcpConfig) {
        this.project = project;
        this.mcpConfig = mcpConfig;
    }

    public void addRepository() {
        Repository repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
                .filter(ArtifactIdentifier.groupEquals(GROUP))
                .filter(ArtifactIdentifier.nameEquals(NAME))
                .filter(ArtifactIdentifier.extensionEquals(EXTENSION))
                .provide(identifier -> {
                    try {
                        return getMCPMinecraft(identifier);
                    } catch (Exception e) {
                        return Artifact.none();
                    }
                })
        );

        HashValue configHash = HashUtil.sha1(mcpConfig);
        GradleRepositoryAdapter.add(project.getRepositories(), NAME, "http://" + configHash.asHexString() + ".mcp.generated.com", repo);
    }

    private Artifact getMCPMinecraft(ArtifactIdentifier identifier) throws Exception {
        String version = identifier.getVersion();
        String pipeline = identifier.getClassifier();

        // Read JSON
        JsonObject json = null; // TODO: Read JSON

        // Load config
        MCPConfig config = MCPConfig.deserialize(project, mcpConfig, json, pipeline);

        // Execute the MCP runtime with the config and return the artifact
        MCPRuntime runtime = new MCPRuntime(project, config, true, Collections.emptyMap());
        File out = runtime.execute(project.getLogger());
        return StreamableArtifact.ofFile(
                new SimpleArtifactIdentifier(GROUP, NAME, version, pipeline, EXTENSION),
                ArtifactType.BINARY,
                out
        );
//        return Artifact.none();
    }

}

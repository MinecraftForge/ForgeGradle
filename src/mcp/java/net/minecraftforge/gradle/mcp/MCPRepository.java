package net.minecraftforge.gradle.mcp;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.gson.JsonObject;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.mcp.task.LoadMCPConfigTask;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import org.gradle.api.Project;
import org.gradle.internal.hash.HashUtil;

import java.io.File;
import java.util.Collections;

public class MCPRepository {

    private static final String GROUP = "net.minecraft";
    private static final String NAME = "mcpgenerated";
    private static final String NAME_EXTRAS = "mcpgenerated-extra";
    private static final String EXTENSION = "jar";
    private static final String EXTENSION_POM = "pom";

    private final Project project;
    private final String mcVersion;
    private final String pipeline;
    private final String atHash;

    private final MCPRuntime runtime;

    private final String artifactName, extrasName;
    private File archive, extras;

    public MCPRepository(Project project, File mcpConfig, String mcVersion, String pipeline, String at) {
        this.project = project;
        this.mcVersion = mcVersion;
        this.pipeline = pipeline;
        this.atHash = HashUtil.sha1(at.getBytes()).asHexString();

        // TODO: Handle access transformers, because we still don't do that
        JsonObject json = LoadMCPConfigTask.readConfig(mcpConfig);
        MCPConfig config = MCPConfig.deserialize(project, mcpConfig, json, pipeline);
        File mcpDirectory = new File(project.getGradle().getGradleHomeDir(), "caches/mcp/" + mcVersion + "/" + pipeline + "/" + atHash);
        this.runtime = new MCPRuntime(project, config, mcpDirectory, true, Collections.emptyMap());

        this.artifactName = GROUP + ":" + NAME + ":" + mcVersion + ":" + pipeline + "." + atHash + "@" + EXTENSION;
        this.extrasName = GROUP + ":" + NAME_EXTRAS + ":" + mcVersion + ":" + pipeline + "." + atHash + "@" + EXTENSION;
    }

    public String getArtifactName() {
        return artifactName;
    }

    public String getExtrasArtifactName() {
        return extrasName;
    }

    public void addRepository() {
        Repository repo = identifier -> {
            if (!identifier.getGroup().equals(GROUP)) return Artifact.none();

            if (identifier.getName().equals(NAME) && identifier.getClassifier().equals(pipeline + "." + atHash)) {
                switch (identifier.getExtension()) {
                    case EXTENSION:
                        try {
                            return StreamableArtifact.ofFile(identifier, ArtifactType.BINARY, getArchive());
                        } catch (Exception ignored) {
                            return Artifact.none();
                        }
                    case EXTENSION_POM:
                        return StreamableArtifact.ofBytes(identifier, ArtifactType.OTHER, getArchivePOM());
                    default:
                        return Artifact.none();
                }
            } else if (identifier.getName().equals(NAME_EXTRAS)) {
                switch (identifier.getExtension()) {
                    case EXTENSION:
                        try {
                            return StreamableArtifact.ofFile(identifier, ArtifactType.BINARY, getExtras());
                        } catch (Exception ignored) {
                            return Artifact.none();
                        }
                    case EXTENSION_POM:
                        return StreamableArtifact.ofBytes(identifier, ArtifactType.OTHER, getExtrasPOM());
                    default:
                        return Artifact.none();
                }
            }

            return Artifact.none();
        };
        GradleRepositoryAdapter.add(project.getRepositories(), NAME, "http://mcp.generated.com", repo);
    }

    private void execute() throws Exception {
        // TODO: Generate and store extras file
        archive = runtime.execute(project.getLogger());
    }

    private File getArchive() throws Exception {
        if (archive == null) execute();
        return archive;
    }

    private File getExtras() throws Exception {
        if (extras == null) execute();
        return extras;
    }

    private byte[] getArchivePOM() {
        POMBuilder pom = new POMBuilder(GROUP, NAME, mcVersion);
        // TODO: Parse and add all the MC dependencies
        // Reference: https://github.com/amadornes/NewForgeGradle/blob/master/src/shared/java/net/minecraftforge/gradle/shared/impl/MCLauncherArtifactProvider.java#L133-L161
        return pom.tryBuild().getBytes();
    }

    private byte[] getExtrasPOM() { // This POM just needs the group, name and version - no deps
        return new POMBuilder(GROUP, NAME_EXTRAS, mcVersion).tryBuild().getBytes();
    }

}

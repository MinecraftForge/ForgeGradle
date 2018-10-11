package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;

public abstract class BaseRepo implements ArtifactProvider<ArtifactIdentifier> {
    protected final File cache;
    protected final Logger log;
    protected final String REPO_NAME = getClass().getSimpleName();

    protected BaseRepo(File cache, Logger log) {
        this.cache = cache;
        this.log = log;
    }

    protected File cache(String... path) {
        return new File(cache, String.join(File.separator, path));
    }

    protected String clean(ArtifactIdentifier art) {
        return art.getGroup() + ":" + art.getName() + ":" + art.getVersion() + ":" + art.getClassifier() + "@" + art.getExtension();
    }

    protected void debug(String message) {
        //this.log.lifecycle(message);
    }
    protected void info(String message) {
        this.log.lifecycle(message);
    }

    @Override
    public final Artifact getArtifact(ArtifactIdentifier artifact) {
        try {
            debug(REPO_NAME + " Request: " + clean(artifact));
            File ret = findFile(artifact);

            if (ret != null) {
                ArtifactType type = ArtifactType.OTHER;
                if (artifact.getClassifier() != null && artifact.getClassifier().endsWith("sources"))
                    type = ArtifactType.SOURCE;
                else if ("jar".equals(artifact.getExtension()))
                    type = ArtifactType.BINARY;

                String[] pts  = artifact.getExtension().split("\\.");
                if (pts.length == 1)
                    return StreamableArtifact.ofFile(artifact, type, ret);
                else if (pts.length == 2) {
                    File hash = new File(ret.getAbsolutePath() + "." + pts[1]);
                    if (hash.exists())
                        return StreamableArtifact.ofFile(artifact, type, hash);
                }
            }
            return Artifact.none();
        } catch (Throwable e) {
            //Catch everything so we don't error up in gradle and fuck up the internals so it never asks us for anything ever again!
            log.lifecycle("Error getting artifact: " + clean(artifact) + " from  " + REPO_NAME, e);
            return Artifact.none();
        }
    }

    protected abstract File findFile(ArtifactIdentifier artifact) throws IOException;

    public static class Builder {
        private List<ArtifactProvider<ArtifactIdentifier>> repos = new ArrayList<>();
        public Builder add(ArtifactProvider<ArtifactIdentifier> repo) {
            if (repo != null)
                repos.add(repo);
            return this;
        }

        public void attach(Project project) {
            int random = new Random().nextInt();
            GradleRepositoryAdapter.add(project.getRepositories(), "BUNDELED_" + random, "http://bundeled_" + random + ".fake/",
                SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(
                    new ArtifactProvider<ArtifactIdentifier>() {
                        @Override
                        public Artifact getArtifact(ArtifactIdentifier artifact) {
                            return repos.stream().map(repo -> repo.getArtifact(artifact)).filter(art -> art != Artifact.none()).findFirst().orElse(Artifact.none());
                        }
                    }
                ))
            );
        }
    }

}

package net.minecraftforge.gradle.mcp;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.common.collect.Maps;

import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.ManifestJson;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Provides the following artifacts:
 *
 * net.minecraft:
 *   client:
 *     MCPVersion:
 *       srg - Srg named SLIM jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *   server:
 *     MCPVersion:
 *       srg - Srg named SLIM jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *   joined:
 *     MCPVersion:
 *       .pom - Pom meta linking against net.minecraft:client:extra and net.minecraft:client:data
 *       '' - Notch named merged jar file
 *       srg - Srg named jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *
 * Note: It does NOT provide the Obfed named jars for server and client, as that is provided by MinecraftRepo.
 */
public class MCPRepo extends BaseRepo {
    private static MCPRepo INSTANCE = null;
    private static final String GROUP = "net.minecraft";
    private static final String STEP_MERGE = "merge"; //TODO: Design better way to get steps output, for now hardcode
    private static final String STEP_RENAME = "rename";

    private final Project project;
    private final Repository repo;
    private final Map<String, MCPWrapper> wrappers = Maps.newHashMap();

    private MCPRepo(Project project, File cache, Logger log) {
        super(cache, log);
        this.project = project;
        this.repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .filter(ArtifactIdentifier.groupEquals(GROUP))
            .filter(ArtifactIdentifier.nameMatches("^(client|server|joined)$"))
            .provide(this)
        );
    }

    private static MCPRepo getInstance(Project project) {
        if (INSTANCE == null)
            INSTANCE = new MCPRepo(project, Utils.getCache(project, "mcp_repo"), project.getLogger());
        return INSTANCE;
    }
    public static void attach(Project project) {
        GradleRepositoryAdapter.add(project.getRepositories(), "MCP_DYNAMIC", "http://mcp_dynamic.fake/", getInstance(project).repo);
    }

    public static ArtifactProvider<ArtifactIdentifier> create(Project project) {
        return getInstance(project);
    }

    private File cacheMC(String side, String version, String classifier, String ext) {
        if (classifier != null)
            return cache("net", "minecraft", side, version, side + '-' + version + '-' + classifier + '.' + ext);
        return cache("net", "minecraft", side, version, side + '-' + version + '.' + ext);
    }

    @SuppressWarnings("unused")
    private File cacheMCP(String side, String version, String classifier, String ext) {
        if (classifier != null)
            return cache("de", "oceanlabs", "mcp", side, version, side + '-' + version + '-' + classifier + '.' + ext);
        return cache("de", "oceanlabs", "mcp", side, version, side + '-' + version + '.' + ext);
    }
    private File cacheMCP(String version) {
        return cache("de", "oceanlabs", "mcp", "mcp_config", version);
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String side = artifact.getName();
        if (!artifact.getGroup().equals(GROUP) || (!"client".equals(side) && !"server".equals(side) && !"joined".equals(side)))
            return null;

        String version = artifact.getVersion();
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension().split("\\.")[0];

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + side + ":" + version + ":" + classifier + "@" + ext);

        if ("pom".equals(ext)) {
            return findPom(side, version);
        } else {
            switch (classifier) {
                case "":              return findRaw(side, version);
                case "srg":           return findSrg(side, version);
            }
        }
        return null;
    }

    private HashStore commonHash(File mcp) {
        return new HashStore(this.cache)
            .add("mcp", mcp);
    }

    private File getMCP(String version) {
        return MavenArtifactDownloader.single(project, "de.oceanlabs.mcp:mcp_config:" + version + "@zip");
    }

    private String getMCVersion(String version) {
        int idx = version.indexOf('-');
        if (idx != -1)
            return version.substring(0, idx);
        return version;
    }

    private File findVersion(String version) throws IOException {
        File manifest = cache("versions", "manifest.json");
        if (!manifest.exists() || manifest.lastModified() < System.currentTimeMillis() - MinecraftRepo.CACHE_TIMEOUT) {
            FileUtils.copyURLToFile(new URL(MinecraftRepo.MANIFEST_URL), manifest);
            Utils.updateHash(manifest);
        }
        File json = cache("versions", version, "version.json");
        if (!json.exists() || json.lastModified() < System.currentTimeMillis() - MinecraftRepo.CACHE_TIMEOUT) {
            URL url =  Utils.loadJson(manifest, ManifestJson.class).getUrl(version);
            if (url != null) {
                FileUtils.copyURLToFile(url, json);
                Utils.updateHash(json);
            } else {
                throw new RuntimeException("Missing version from manifest: " + version);
            }
        }
        return json;
    }

    private File findPom(String side, String version) throws IOException {
        File json = findVersion(getMCVersion(version));
        File mcp = getMCP(version);

        File pom = cacheMC(side, version, null, "pom");
        debug("    Finding pom: " + pom);
        HashStore cache = commonHash(mcp).load(cacheMC(side, version, null, "pom.input"));
        if (!"server".equals(side))
            cache.add("json", json);

        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, side, version);
            if (!"server".equals(side)) {
                VersionJson meta = Utils.loadJson(json, VersionJson.class);
                for (VersionJson.Library lib : meta.libraries) {
                    //TODO: Filter?
                    builder.dependencies().add(lib.name, "compile");
                    if (lib.downloads.classifiers != null) {
                        if (lib.downloads.classifiers.containsKey("test")) {
                            builder.dependencies().add(lib.name, "test").withClassifier("test");
                        }
                        if (lib.natives != null && lib.natives.containsKey(MinecraftRepo.CURRENT_OS)) {
                            builder.dependencies().add(lib.name, "runtime").withClassifier(lib.natives.get(MinecraftRepo.CURRENT_OS));
                        }
                    }
                }
                builder.dependencies().add("net.minecraft:client:" + getMCVersion(version), "compile").withClassifier("extra");
                builder.dependencies().add("net.minecraft:client:" + getMCVersion(version), "compile").withClassifier("data");
            } else {
                builder.dependencies().add("net.minecraft:server:" + getMCVersion(version), "compile").withClassifier("extra");
                builder.dependencies().add("net.minecraft:server:" + getMCVersion(version), "compile").withClassifier("data");
            }

            MCPWrapper wrapper = getWrapper(version, mcp);
            wrapper.getConfig().getLibraries(side).forEach(e -> builder.dependencies().add(e, "compile"));

            String ret = builder.tryBuild();
            if (ret == null)
                return null;
            FileUtils.writeByteArrayToFile(pom, ret.getBytes());
            cache.save();
            Utils.updateHash(pom);
        }

        return pom;
    }

    private File findRaw(String side, String version) throws IOException {
        if (!"joined".equals(side))
            return null; //MinecraftRepo provides these

        return findStepOutput(side, version, null, "jar", STEP_MERGE);
    }

    private File findSrg(String side, String version) throws IOException {
        return findStepOutput(side, version, "srg", "jar", STEP_RENAME);
    }

    private File findStepOutput(String side, String version, String classifier, String ext, String step) throws IOException {
        File mcp = getMCP(version);
        File raw = cacheMC(side, version, classifier, ext);
        debug("  Finding " + step + ": " + raw);
        HashStore cache = commonHash(mcp).load(cacheMC(side, version, classifier, ext + ".input"));

        if (!cache.isSame() || !raw.exists()) {
            MCPWrapper wrapper = getWrapper(version, mcp);
            MCPRuntime runtime = wrapper.getRuntime(project, side);
            try {
                File output = runtime.execute(log, step);
                FileUtils.copyFile(output, raw);
                cache.save();
                Utils.updateHash(raw);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                log.lifecycle(e.getMessage());
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new RuntimeException(e);
            }
        }
        return raw;
    }

    private synchronized MCPWrapper getWrapper(String version, File data) throws IOException {
        String hash = HashFunction.SHA1.hash(data);
        MCPWrapper ret = wrappers.get(version);
        if (ret == null  || !hash.equals(ret.getHash())) {
            ret = new MCPWrapper(hash, data, cacheMCP(version));
            wrappers.put(version, ret);
        }
        return ret;
    }
}

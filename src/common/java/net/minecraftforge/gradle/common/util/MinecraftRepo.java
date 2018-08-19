package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;

import net.minecraftforge.gradle.common.util.VersionJson.Download;
import net.minecraftforge.gradle.common.util.VersionJson.OS;

import static net.minecraftforge.gradle.common.util.HashFunction.*;

public class MinecraftRepo implements ArtifactProvider<ArtifactIdentifier> {
    private static MinecraftRepo INSTANCE;
    private static int CACHE_TIMEOUT = 1000 * 60 * 60 * 1; //1 hour, Timeout used for version_manifest.json so we dont ping their server every request.
                                                           //manifest doesn't include sha1's so we use this for the per-version json as well.
    private static final String GROUP = "net.minecraft";
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String CURRENT_OS = OS.getCurrent().getName();

    private File cache;
    private Repository repo;
    private Logger log;
    private MinecraftRepo(File cache, Logger log) {
        this.cache = cache;
        this.log = log;
    }

    public static void attach(Project project) {
        if (INSTANCE == null) {
            File cache = Utils.getCache(project, "minecraft_repo");
            INSTANCE = new MinecraftRepo(cache, project.getLogger());
        }
        GradleRepositoryAdapter.add(project.getRepositories(), "MINECRAFT_DYNAMIC", "http://minecraft_dynamic.fake/", INSTANCE.getRepo());
    }

    private Repository getRepo() {
        if (this.repo == null) {
            this.repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
                .filter(ArtifactIdentifier.groupEquals(GROUP))
                .filter(ArtifactIdentifier.nameMatches("^(client|server)$"))
                .provide(this)
            );
        }
        return this.repo;
    }

    private File cache(String... path) {
        return new File(cache, String.join(File.separator, path));
    }

    private void log(String line) {
        if (this.log == null) {
            System.out.println(line);
        } else {
            this.log.lifecycle(line);
        }
    }

    @Override
    public Artifact getArtifact(ArtifactIdentifier artifact) {
        try {
            String side = artifact.getName();

            if (!artifact.getGroup().equals(GROUP) || (!"client".equals(side) && !"server".equals(side))) {
                return Artifact.none();
            }

            String version = artifact.getVersion();
            String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
            String ext = artifact.getExtension().split("\\.")[0];

            File ret = null;
            if ("pom".equals(ext)) {
                ret = findPom(side, version);
            } else {
                switch (classifier) {
                    case "":       ret = findRaw(side, version); break;
                    case "extra":  ret = findExtra(side, version); break;
                }
            }
            if (ret != null) {
                return provideFile(artifact, ret);
            }
            return Artifact.none();
        } catch (IOException e) {
            e.printStackTrace();
            log(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public File findVersion(String version) throws IOException {
        File manifest = cache("versions/manifest.json");
        if (!manifest.exists() || manifest.lastModified() < System.currentTimeMillis() - CACHE_TIMEOUT) {
            FileUtils.copyURLToFile(new URL(MANIFEST_URL), manifest);
            Utils.updateHash(manifest, SHA1);
        }
        File json = cache("versions", version, "version.json");
        if (!json.exists() || json.lastModified() < System.currentTimeMillis() - CACHE_TIMEOUT) {
            URL url =  Utils.loadJson(manifest, ManifestJson.class).getUrl(version);
            if (url != null) {
                FileUtils.copyURLToFile(url, json);
                Utils.updateHash(json, SHA1);
            } else {
                throw new RuntimeException("Missing version from manifest: " + version);
            }
        }
        return json;
    }

    private File findPom(String side, String version) throws IOException {
        File json = findVersion(version);
        File pom = cache("versions", version, side + ".pom");
        HashStore cache = new HashStore(this.cache).load(cache("versions", version, side + ".pom.input"));

        if ("client".equals(side)) {
            cache.add(json);
        }

        //log("POM Cache: " + cache.isSame()  + " " + pom.exists());
        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, side, version);
            if ("client".equals(side)) {
                VersionJson meta = Utils.loadJson(json, VersionJson.class);
                for (VersionJson.Library lib : meta.libraries) {
                    //TODO: Filter?
                    builder.dependencies().add(lib.name, "compile");
                    if (lib.downloads.classifiers != null) {
                        if (lib.downloads.classifiers.containsKey("test")) {
                            builder.dependencies().add(lib.name, "test").withClassifier("test");
                        }
                        if (lib.natives != null && lib.natives.containsKey(CURRENT_OS)) {
                            builder.dependencies().add(lib.name, "runtime").withClassifier(lib.natives.get(CURRENT_OS));
                        }
                    }
                }
            }
            builder.dependencies().add(GROUP + ":" + side + ":" + version, "compile").withClassifier("extra"); //Compile right?
            builder.dependencies().add("com.google.code.findbugs:jsr305:3.0.1", "compile"); //TODO: Pull this from MCPConfig.

            String ret = builder.tryBuild();
            if (ret == null) {
                return null;
            }
            FileUtils.writeByteArrayToFile(pom, ret.getBytes());
            cache.save();
            Utils.updateHash(pom);
        }

        return pom;
    }

    private File findRaw(String side, String version) throws IOException {
        VersionJson json = Utils.loadJson(findVersion(version), VersionJson.class);
        if (json.downloads == null || !json.downloads.containsKey(side)) {
            throw new IllegalStateException(version +".json missing download for " + side);
        }
        Download dl = json.downloads.get(side);
        File raw = cache("versions", version, side + ".jar");
        if (!raw.exists() || !HashFunction.SHA1.hash(raw).equals(dl.sha1)) {
            FileUtils.copyURLToFile(dl.url, raw);
            Utils.updateHash(raw);
        }
        return raw;
    }

    private File findExtra(String side, String version) throws IOException {
        File raw = findRaw(side, version);
        File extra = cache("versions", version, side + "-extra.jar");
        HashStore cache = new HashStore(this.cache).load(cache("versions", version, side + "-extra.input")).add("raw", raw);
        //TODO: For now we just strip all class files in default package, or net/minecraft.
        //      We *should* download the mappings from somewhere and only filter Minecraft's classes.

        if (!cache.isSame() || !extra.exists()) {
            try (ZipFile zin = new ZipFile(raw);
                 FileOutputStream fos = new FileOutputStream(extra);
                 ZipOutputStream out = new ZipOutputStream(fos)) {

                List<String> files = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> entries = zin.entries(); entries.hasMoreElements();) {
                    String name = entries.nextElement().getName();
                    if (!name.endsWith(".class") || (!name.startsWith("net/minecraft/") && name.indexOf('/') != -1)) {
                        files.add(name);
                    }
                }
                Collections.sort(files);
                for (String file : files) {
                    ZipEntry entry = new ZipEntry(file);
                    entry.setTime(0);
                    out.putNextEntry(entry);
                    try (InputStream ein = zin.getInputStream(zin.getEntry(file))) {
                        IOUtils.copy(ein, out);
                    }
                    out.closeEntry();
                }
            }

            cache.save();
            Utils.updateHash(extra);
        }
        return extra;
    }

    private Artifact provideFile(ArtifactIdentifier artifact, File file) {
        ArtifactType type = ArtifactType.OTHER;
        if ("sources".equals(artifact.getClassifier())) {
            type = ArtifactType.SOURCE;
        } else if ("jar".equals(artifact.getExtension())) {
            type = ArtifactType.BINARY;
        }

        String[] pts  = artifact.getExtension().split("\\.");
        if (pts.length == 1) {
            //log(clean(artifact) + " " + file);
            return StreamableArtifact.ofFile(artifact, type, file);
        } else if (pts.length == 2) {
            File hash = new File(file.getAbsolutePath() + "." + pts[1]);
            if (hash.exists()) {
                //log(clean(artifact) + " " + hash);
                return StreamableArtifact.ofFile(artifact, type, hash);
            }
        }
        return Artifact.none();
    }

    @SuppressWarnings("unused")
    private String clean(ArtifactIdentifier art) {
        return art.getGroup() + ":" + art.getName() + ":" + art.getVersion() + ":" + art.getClassifier() + "@" + art.getExtension();
    }

}

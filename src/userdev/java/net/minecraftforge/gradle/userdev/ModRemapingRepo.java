/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.userdev;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.SrgJarRenamer;
import net.minecraftforge.gradle.common.util.Utils;

/*
 * Takes in SRG names jars/sources and remaps them using MCPNames.
 * TODO: Direct file dep support?
 */
public class ModRemapingRepo extends BaseRepo {
    private final Project project;
    private final String MAPPING;
    private final Map<String, McpNames> mapCache = new HashMap<>();
    private final Map<String, Artifact> external = new HashMap<>();
    @SuppressWarnings("unused")
    private Repository repo;

    public ModRemapingRepo(Project project, String mapping) {
        super(Utils.getCache(project, "mod_remap_repo"), project.getLogger());
        this.project = project;
        this.MAPPING = mapping;
        repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(this));
    }

    private File cache(Artifact artifact, String mapping) {
        return cache(artifact, mapping, artifact.getClassifier());
    }
    private File cache(Artifact artifact, String mapping, String classifier) {
        return cache(artifact, mapping, classifier, artifact.getExtension());
    }
    private File cache(Artifact artifact, String mapping, String classifier, String extension) {
        return cache(artifact.getGroup(), artifact.getName(), artifact.getVersion(), classifier, extension, mapping);
    }
    private File cache(String group, String name, String version, String classifier, String ext, String mapping) {
        if (mapping != null)
            version += "_mapped_" + mapping;
        if (ext == null)
            ext = "jar";

        String filename = name + '-' + version;
        if (classifier != null)
            filename += '-' + classifier;
        filename += '.' + ext;

        return cache(group.replace('.', File.separatorChar), name, version, filename);
    }

    public String addDep(String group, String name, String version) {
        String str = group + ':' + name + ':' + version;
        String map = str += "_mapped_" + MAPPING;
        external.put(map, Artifact.from(str));
        return map;
    }

    private String getMappings(String version) {
        if (!version.contains("_mapped_"))
            return null;
        return version.split("_mapped_")[1];
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String group = artifact.getGroup();
        String version = artifact.getVersion();
        String mappings = getMappings(version);

        if (mappings == null)
            return null; //We only care about the remaped files, not orig

        if (mappings != null) // Left this way in case I change my mind
            version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));

        String desc = group + ':' + artifact.getName() + ':' + artifact.getVersion();
        Artifact orig = external.get(desc);
        if (orig == null)
            return null; //Not one of our whitelisted deps

        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension().split("\\.")[0];

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            //return findPom(orig, mappings); //TODO: Unsupported for now, transitive deobfed deps? Need full xml reader?
        } else {
            switch (classifier) {
                case "":        return findRaw(orig, mappings);
                case "sources": return findSource(orig, mappings);
            }
        }
        return null;
    }

    private File findMapping(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
        debug("    Mapping: " + desc);
        return Utils.downloadMaven(project, Artifact.from(desc), false);
    }

    private File findPom(Artifact artifact, String mapping) throws IOException {
        if (mapping == null)
            return null;

        File clean = Utils.downloadMaven(project, Artifact.from(artifact.getGroup() + ':' + artifact.getName() + ':' + artifact.getVersion() + "@pom"), false);
        if (clean == null || !clean.exists())
            return null;

        File pom = cache(artifact, mapping, null, "pom");
        debug("  Finding pom: " + pom);
        HashStore cache = new HashStore()
            .load(new File(pom.getAbsolutePath() + ".input"))
            .add("pom", pom);

        if (!cache.isSame() || !pom.exists()) {
            //TODO: Read the pom and replace the version number? XML reader?
            //FileUtils.writeByteArrayToFile(pom, ret.getBytes());
            cache.save();
            Utils.updateHash(pom, HashFunction.SHA1);
        }

        return pom;
    }

    private File findRaw(Artifact artifact, String mapping) throws IOException {
        File names = findMapping(mapping);
        if (names == null || !names.exists())
            return null;

        File orig = Utils.downloadMaven(project, artifact, false);
        if (orig == null || !orig.exists())
            return null;

        HashStore cache = new HashStore()
            .load(cache(artifact, mapping, null, "jar.input"))
            .add("names", names)
            .add("orig", orig);

        File ret = cache(artifact, mapping, null, "jar");
        if (!cache.isSame() || !ret.exists()) {
            SrgJarRenamer.rename(orig, ret, names);

            Utils.updateHash(ret, HashFunction.SHA1);
            cache.save();
        }
        return ret;
    }

    private File findSource(Artifact artifact, String mapping) throws IOException {
        File names = findMapping(mapping);
        if (names == null || !names.exists())
            return null;

        File orig = Utils.downloadMaven(project, Artifact.from(artifact.getGroup() + ':' + artifact.getName() + ':' + artifact.getVersion() + ":sources"), false);
        if (orig == null || !orig.exists())
            return null;

        HashStore cache = new HashStore()
            .load(cache(artifact, mapping, "sources", "jar.input"))
            .add("names", names)
            .add("orig", orig);

        File ret = cache(artifact, mapping, "sources", "jar");
        if (!cache.isSame() || !ret.exists()) {
            McpNames map = McpNames.load(names);

            if (!ret.getParentFile().exists())
                ret.getParentFile().mkdirs();

            try(ZipInputStream zin = new ZipInputStream(new FileInputStream(orig));
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(ret))) {
                ZipEntry _old;
                while ((_old = zin.getNextEntry()) != null) {
                    ZipEntry _new = new ZipEntry(_old.getName());
                    _new.setTime(0);
                    zout.putNextEntry(_new);

                    if (_old.getName().endsWith(".java")) {
                        String mapped = map.rename(zin, true);
                        IOUtils.write(mapped, zout);
                    } else {
                        IOUtils.copy(zin, zout);
                    }
                }
            }

            Utils.updateHash(ret, HashFunction.SHA1);
            cache.save();
        }
        return ret;
    }
}

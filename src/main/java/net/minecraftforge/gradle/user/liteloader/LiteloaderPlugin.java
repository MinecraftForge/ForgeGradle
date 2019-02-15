/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.user.liteloader;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.user.UserConstants.*;

import net.minecraftforge.gradle.user.UserVanillaBasePlugin;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.gradle.util.json.JsonFactory;
import net.minecraftforge.gradle.util.json.LiteLoaderJson;
import net.minecraftforge.gradle.util.json.LiteLoaderJson.Artifact;
import net.minecraftforge.gradle.util.json.LiteLoaderJson.RepoObject;
import net.minecraftforge.gradle.util.json.LiteLoaderJson.VersionObject;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.tasks.Jar;

import java.util.List;
import java.util.Map;

public class LiteloaderPlugin extends UserVanillaBasePlugin<LiteloaderExtension>
{
    public static final String CONFIG_LL_DEOBF_COMPILE = "liteloaderDeobfCompile";
    public static final String CONFIG_LL_DC_RESOLVED = "liteloaderResolvedDeobfCompile";

    public static final String MAVEN_REPO_NAME = "liteloaderRepo";

    public static final String MODFILE_PREFIX = "mod-";
    public static final String MODFILE_EXTENSION = "litemod";

    public static final String VERSION_JSON_URL = "http://dl.liteloader.com/versions/versions.json";
    public static final String VERSION_JSON_FILENAME = "versions.json";
    public static final String VERSION_JSON_FILE = REPLACE_CACHE_DIR + "/com/mumfrey/liteloader/" + VERSION_JSON_FILENAME;

    public static final String TASK_LITEMOD = "litemod";

    public static final String MFATT_MODTYPE = "ModType";
    public static final String MODSYSTEM = "LiteLoader";

    private LiteLoaderJson json;

    private RepoObject repo;

    private Artifact artifact;

    @Override
    protected void applyVanillaUserPlugin()
    {
        final ConfigurationContainer configs = this.project.getConfigurations();
        configs.maybeCreate(CONFIG_LL_DEOBF_COMPILE);
        configs.maybeCreate(CONFIG_LL_DC_RESOLVED);

        configs.getByName(CONFIG_DC_RESOLVED).extendsFrom(configs.getByName(CONFIG_LL_DC_RESOLVED));

        final DelayedFile versionJson = delayedFile(VERSION_JSON_FILE);
        final DelayedFile versionJsonEtag = delayedFile(VERSION_JSON_FILE + ".etag");
        setJson(JsonFactory.loadLiteLoaderJson(getWithEtag(VERSION_JSON_URL, versionJson.call(), versionJsonEtag.call())));

        String baseName = MODFILE_PREFIX + this.project.property("archivesBaseName").toString().toLowerCase();

        TaskContainer tasks = this.project.getTasks();
        final Jar jar = (Jar)tasks.getByName("jar");
        jar.setExtension(MODFILE_EXTENSION);
        jar.setBaseName(baseName);

        final Jar sourceJar = (Jar)tasks.getByName("sourceJar");
        sourceJar.setBaseName(baseName);

        makeTask(TASK_LITEMOD, LiteModTask.class);
    }

    @Override
    protected void afterEvaluate()
    {
        super.afterEvaluate();
        this.applyJson();

        // If user has changed extension back to .jar, write the ModType
        // manifest attribute
        final Jar jar = (Jar)this.project.getTasks().getByName("jar");
        if ("jar".equals(jar.getExtension())) {
            Attributes attributes = jar.getManifest().getAttributes();
            if (attributes.get(MFATT_MODTYPE) == null) {
                attributes.put(MFATT_MODTYPE, MODSYSTEM);
            }
        }
    }

    @Override
    protected void setupDevTimeDeobf(final Task compileDummy, final Task providedDummy)
    {
        super.setupDevTimeDeobf(compileDummy, providedDummy);

        // die with error if I find invalid types...
        this.project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                if (project.getState().getFailure() != null)
                    return;

                remapDeps(project, project.getConfigurations().getByName(CONFIG_LL_DEOBF_COMPILE), CONFIG_LL_DC_RESOLVED, compileDummy);
            }
        });
    }

    private void applyJson()
    {
        if (this.json == null)
        {
            return;
        }

        VersionObject version = this.json.versions.get(this.getExtension().getVersion());
        if (version != null)
        {
            this.setRepo(version.repo);
            this.setArtifact(version.latest);
            this.applyDependenciesFromJson();
        }
    }

    private void applyDependenciesFromJson()
    {
        final RepoObject repo = this.getRepo();
        if (repo == null)
        {
            return;
        }

        this.project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                addMavenRepo(proj, MAVEN_REPO_NAME, repo.url);
            }
        });

        Artifact artifact = this.getArtifact();
        if (artifact == null)
        {
            return;
        }
        addDependency(this.project, CONFIG_LL_DEOBF_COMPILE, artifact.getDepString(repo));

        for (Map<String, String> library : artifact.getLibraries())
        {
            String name = library.get("name");
            if (name != null && !name.isEmpty())
            {
                addDependency(this.project, CONFIG_MC_DEPS, name);
            }

            String url = library.get("url");
            if (url != null && !url.isEmpty())
            {
                addMavenRepo(this.project, url, url);
            }
        }
    }

    public VersionObject getVersion(String version)
    {
        return this.json != null ? this.json.versions.get(version) : null;
    }

    public LiteLoaderJson getJson()
    {
        return this.json;
    }

    public void setJson(LiteLoaderJson json)
    {
        this.json = json;
    }

    public RepoObject getRepo()
    {
        return this.repo;
    }

    public void setRepo(RepoObject repo)
    {
        this.repo = repo;
    }

    public Artifact getArtifact()
    {
        return this.artifact;
    }

    public void setArtifact(Artifact artifact)
    {
        this.artifact = artifact;
    }

    @Override
    protected String getJarName()
    {
        return "minecraft";
    }

    @Override
    protected void createDecompTasks(String globalPattern, String localPattern)
    {
        super.makeDecompTasks(globalPattern, localPattern, delayedFile(JAR_CLIENT_FRESH), TASK_DL_CLIENT, delayedFile(MCP_PATCHES_CLIENT), delayedFile(MCP_INJECT));
    }

    @Override
    protected boolean hasServerRun()
    {
        return false;
    }

    @Override
    protected boolean hasClientRun()
    {
        return true;
    }

    @Override
    protected Object getStartDir()
    {
        return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/" + getJarName() + "/" + REPLACE_MC_VERSION + "/start");
    }

    @Override
    protected String getClientTweaker(LiteloaderExtension ext)
    {
        return "com.mumfrey.liteloader.launch.LiteLoaderTweaker";
    }

    @Override
    protected String getClientRunClass(LiteloaderExtension ext)
    {
        return "com.mumfrey.liteloader.debug.Start";
    }

    @Override
    protected String getServerTweaker(LiteloaderExtension ext)
    {
        return "";// never run on server.. so...
    }

    @Override
    protected String getServerRunClass(LiteloaderExtension ext)
    {
        // irrelevant..
        return "";
    }

    @Override
    protected List<String> getClientJvmArgs(LiteloaderExtension ext)
    {
        return ext.getResolvedClientJvmArgs();
    }

    @Override
    protected List<String> getServerJvmArgs(LiteloaderExtension ext)
    {
        return ext.getResolvedServerJvmArgs();
    }

    protected void addDependency(Project proj, String configuration, String dependency)
    {
        proj.getDependencies().add(configuration, dependency);
    }
}

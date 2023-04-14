/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.legacy.LegacyExtension;
import net.minecraftforge.gradle.common.tasks.ExtractNatives;
import net.minecraftforge.gradle.common.tasks.ide.CopyEclipseResources;
import net.minecraftforge.gradle.common.tasks.ide.CopyIntellijResources;
import net.minecraftforge.gradle.common.util.VersionJson.Download;
import net.minecraftforge.gradle.common.util.runs.RunConfigGenerator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import groovy.lang.Closure;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.idea.IdeaPlugin;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class Utils {
    private static final boolean ENABLE_FILTER_REPOS = Boolean.parseBoolean(System.getProperty("net.minecraftforge.gradle.filter_repos", "true"));

    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MCPConfigV1.Step.class, new MCPConfigV1.Step.Deserializer())
        .registerTypeAdapter(VersionJson.Argument.class, new VersionJson.Argument.Deserializer())
        .setPrettyPrinting().create();
    static final int CACHE_TIMEOUT = 1000 * 60 * 60; //1 hour, Timeout used for version_manifest.json so we dont ping their server every request.
                                                          //manifest doesn't include sha1's so we use this for the per-version json as well.
    public static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    public static final String MOJANG_MAVEN = "https://libraries.minecraft.net/";
    public static final String BINPATCHER =  "net.minecraftforge:binarypatcher:1.+:fatjar";
    public static final String ACCESSTRANSFORMER = "net.minecraftforge:accesstransformers:8.0.+:fatjar";
    public static final String SPECIALSOURCE = "net.md-5:SpecialSource:1.11.0:shaded";
    public static final String FART = "net.minecraftforge:ForgeAutoRenamingTool:0.1.+:all";
    public static final String SRG2SOURCE =  "net.minecraftforge:Srg2Source:8.+:fatjar";
    public static final String SIDESTRIPPER = "net.minecraftforge:mergetool:1.1.6:fatjar";
    public static final String INSTALLERTOOLS = "net.minecraftforge:installertools:1.3.2:fatjar";
    public static final String JARCOMPATIBILITYCHECKER = "net.minecraftforge:JarCompatibilityChecker:0.1.+:all";
    public static final long ZIPTIME = 628041600000L;
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    public static void extractFile(ZipFile zip, String name, File output) throws IOException {
        extractFile(zip, zip.getEntry(name), output);
    }

    public static void extractFile(ZipFile zip, ZipEntry entry, File output) throws IOException {
        File parent = output.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        try (InputStream stream = zip.getInputStream(entry)) {
            Files.copy(stream, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void extractDirectory(Function<String, File> fileLocator, ZipFile zip, String directory) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;
            if (!e.getName().startsWith(directory)) continue;
            extractFile(zip, e, fileLocator.apply(e.getName()));
        }
    }

    public static Set<String> copyZipEntries(ZipOutputStream zout, ZipInputStream zin, Predicate<String> filter) throws IOException {
        Set<String> added = new HashSet<>();
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            if (!filter.test(entry.getName())) continue;
            ZipEntry _new = new ZipEntry(entry.getName());
            _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
            zout.putNextEntry(_new);
            IOUtils.copy(zin, zout);
            added.add(entry.getName());
        }
        return added;
    }

    public static byte[] base64DecodeStringList(List<String> strings) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String string : strings) {
            bos.write(Base64.getDecoder().decode(string));
        }
        return bos.toByteArray();
    }

    public static File delete(File file) {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (file.exists()) file.delete();
        return file;
    }

    public static File createEmpty(File file) throws IOException {
        delete(file);
        file.createNewFile();
        return file;
    }

    public static Path getCacheBase(Project project) {
        File gradleUserHomeDir = project.getGradle().getGradleUserHomeDir();
        return Paths.get(gradleUserHomeDir.getPath(), "caches", "forge_gradle");
    }

    public static File getCache(Project project, String... tail) {
        return Paths.get(getCacheBase(project).toString(), tail).toFile();
    }

    public static void extractZip(File source, File target, boolean overwrite) throws IOException {
        extractZip(source, target, overwrite, false);
    }

    public static void extractZip(File source, File target, boolean overwrite, boolean deleteExtras) throws IOException {
        extractZip(source, target, overwrite, deleteExtras, name -> name);
    }

    public static void extractZip(File source, File target, boolean overwrite, boolean deleteExtras, Function<String, String> renamer) throws IOException {
        Set<File> extra = deleteExtras ? Files.walk(target.toPath()).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toSet()) : new HashSet<>();

        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry e = enu.nextElement();
                if (e.isDirectory()) continue;

                String name = renamer.apply(e.getName());
                if (name == null) continue;
                File out = new File(target, name);

                File parent = out.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                extra.remove(out);

                if (out.exists()) {
                    if (!overwrite)
                        continue;

                    //Reading is fast, and prevents Disc wear, so check if it's equals before writing.
                    try (FileInputStream fis = new FileInputStream(out)){
                        if (IOUtils.contentEquals(zip.getInputStream(e), fis))
                            continue;
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    IOUtils.copy(zip.getInputStream(e), fos);
                }
            }
        }

        if (deleteExtras) {
            extra.forEach(File::delete);

            //Delete empty directories
            Files.walk(target.toPath())
            .filter(Files::isDirectory)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .filter(f -> f.list().length == 0)
            .forEach(File::delete);
        }
    }

    public static File updateDownload(Project project, File target, Download dl) throws IOException {
        if (!target.exists() || !HashFunction.SHA1.hash(target).equals(dl.sha1)) {
            project.getLogger().lifecycle("Downloading: " + dl.url);

            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }

            FileUtils.copyURLToFile(dl.url, target);
        }
        return target;
    }

    public static <T> T loadJson(File target, Class<T> clz) throws IOException {
        try (InputStream in = new FileInputStream(target)) {
            return GSON.fromJson(new InputStreamReader(in), clz);
        }
    }
    public static <T> T loadJson(InputStream in, Class<T> clz) {
        return GSON.fromJson(new InputStreamReader(in), clz);
    }

    public static void updateHash(File target) throws IOException {
        updateHash(target, HashFunction.values());
    }
    public static void updateHash(File target, HashFunction... functions) throws IOException {
        for (HashFunction function : functions) {
            File cache = new File(target.getAbsolutePath() + "." + function.getExtension());
            if (target.exists()) {
                String hash = function.hash(target);
                Files.write(cache.toPath(), hash.getBytes());
            } else if (cache.exists()) {
                cache.delete();
            }
        }
    }

    public static void forZip(ZipFile zip, IOConsumer<ZipEntry> consumer) throws IOException {
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            consumer.accept(entries.nextElement());
        }
    }
    @FunctionalInterface
    public interface IOConsumer<T> {
        void accept(T value) throws IOException;
    }

    /**
     * Resolves the supplied object to a string.
     * If the input is null, this will return null.
     * Closures and Callables are called with no arguments.
     * Arrays use Arrays.toString().
     * File objects return their absolute paths.
     * All other objects have their toString run.
     * @param obj Object to resolve
     * @return resolved string
     */
    @SuppressWarnings("rawtypes")
    @Nullable
    public static String resolveString(@Nullable Object obj) {
        if (obj == null)
            return null;
        else if (obj instanceof String) // stop early if its the right type. no need to do more expensive checks
            return (String)obj;
        else if (obj instanceof Closure)
            return resolveString(((Closure)obj).call());// yes recursive.
        else if (obj instanceof Callable) {
            try {
                return resolveString(((Callable)obj).call());
            } catch (Exception e) {
                return null;
            }
        } else if (obj instanceof File)
            return ((File) obj).getAbsolutePath();
        else if (obj.getClass().isArray()) { // arrays
            if (obj instanceof Object[])
                return Arrays.toString(((Object[]) obj));
            else if (obj instanceof byte[])
                return Arrays.toString(((byte[]) obj));
            else if (obj instanceof char[])
                return Arrays.toString(((char[]) obj));
            else if (obj instanceof int[])
                return Arrays.toString(((int[]) obj));
            else if (obj instanceof float[])
                return Arrays.toString(((float[]) obj));
            else if (obj instanceof double[])
                return Arrays.toString(((double[]) obj));
            else if (obj instanceof long[])
                return Arrays.toString(((long[]) obj));
            else
                return obj.getClass().getSimpleName();
        }
        return obj.toString();
    }

    public static <T> T[] toArray(JsonArray array, Function<JsonElement, T> adapter, IntFunction<T[]> arrayFactory) {
        return StreamSupport.stream(array.spliterator(), false).map(adapter).toArray(arrayFactory);
    }

    public static byte[] getZipData(File file, String name) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null)
                throw new IOException("Zip Missing Entry: " + name + " File: " + file);

            return IOUtils.toByteArray(zip.getInputStream(entry));
        }
    }


    public static <T> T fromJson(InputStream stream, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(new InputStreamReader(stream), classOfT);
    }
    public static <T> T fromJson(byte[] data, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), classOfT);
    }

    @Nonnull
    public static String capitalize(@Nonnull final String toCapitalize) {
        return toCapitalize.length() > 1 ? toCapitalize.substring(0, 1).toUpperCase() + toCapitalize.substring(1) : toCapitalize;
    }

    public static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static ZipEntry getStableEntry(String name) {
        return getStableEntry(name, Utils.ZIPTIME);
    }

    public static ZipEntry getStableEntry(String name, long time) {
        TimeZone _default = TimeZone.getDefault();
        TimeZone.setDefault(GMT);
        ZipEntry ret = new ZipEntry(name);
        ret.setTime(time);
        TimeZone.setDefault(_default);
        return ret;
    }

    public static void createRunConfigTasks(final MinecraftExtension extension, final TaskProvider<ExtractNatives> extractNatives, final TaskProvider<?>... setupTasks) {
        List<TaskProvider<?>> setupTasksLst = new ArrayList<>(Arrays.asList(setupTasks));

        final TaskProvider<Task> prepareRuns = extension.getProject().getTasks().register("prepareRuns", Task.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(extractNatives, setupTasksLst);
        });

        final TaskProvider<Task> makeSrcDirs = extension.getProject().getTasks().register("makeSrcDirs", Task.class, task ->
                task.doFirst(t -> {
                    final JavaPluginExtension java = task.getProject().getExtensions().getByType(JavaPluginExtension.class);

                    java.getSourceSets().forEach(s -> s.getAllSource()
                            .getSrcDirs().stream().filter(f -> !f.exists()).forEach(File::mkdirs));
                }));
        setupTasksLst.add(makeSrcDirs);

        extension.getRuns().forEach(RunConfig::mergeParents);

        // Create run configurations _AFTER_ all projects have evaluated so that _ALL_ run configs exist and have been configured
        extension.getProject().getGradle().projectsEvaluated(gradle -> {
            VersionJson json = null;

            try {
                json = Utils.loadJson(extractNatives.get().getMeta().get().getAsFile(), VersionJson.class);
            } catch (IOException ignored) {
            }

            List<String> additionalClientArgs = json != null ? json.getPlatformJvmArgs() : Collections.emptyList();

            extension.getRuns().forEach(RunConfig::mergeChildren);
            extension.getRuns().forEach(run -> RunConfigGenerator.createRunTask(run, extension.getProject(), prepareRuns, additionalClientArgs));

            EclipseHacks.doEclipseFixes(extension, extractNatives, setupTasksLst);
            LegacyExtension.runRetrogradleFixes(extension.getProject());

            RunConfigGenerator.createIDEGenRunsTasks(extension, prepareRuns, makeSrcDirs, additionalClientArgs);
        });
    }

    public static void addRepoFilters(Project project) {
        if (!ENABLE_FILTER_REPOS) return;

        if (project.getGradle().getStartParameter().getTaskNames().stream().anyMatch(t -> t.endsWith("DownloadSources"))) {
            // Only modify repos already present to fix issues with IntelliJ's download sources
            project.getRepositories().forEach(Utils::addMappedFilter);
        } else {
            // Modify Repos already present and when they get added
            project.getRepositories().all(Utils::addMappedFilter);
        }
    }

    private static void addMappedFilter(ArtifactRepository repository) {
        // Skip our "Fake" Repos that actually do provide the de-obfuscated Artifacts
        if (repository instanceof GradleRepositoryAdapter) return;

        // Exclude Artifacts that are being de-obfuscated via ForgeGradle (_mapped_ in version)
        repository.content(rcd -> rcd.excludeVersionByRegex(".*", ".*", ".*_mapped_.*"));
    }

    public static File getMCDir()
    {
        switch (VersionJson.OS.getCurrent()) {
            case OSX:
                return new File(System.getProperty("user.home") + "/Library/Application Support/minecraft");
            case WINDOWS:
                return new File(System.getenv("APPDATA") + "\\.minecraft");
            case LINUX:
            default:
                return new File(System.getProperty("user.home") + "/.minecraft");
        }
    }

    public static String replaceTokens(Map<String, ?> tokens, String value) {
        StringBuilder buf = new StringBuilder();

        for (int x = 0; x < value.length(); x++) {
            char c = value.charAt(x);
            if (c == '\\') {
                if (x == value.length() - 1)
                    throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                buf.append(value.charAt(++x));
            } else if (c == '{' || c ==  '\'') {
                StringBuilder key = new StringBuilder();
                for (int y = x + 1; y <= value.length(); y++) {
                    if (y == value.length())
                        throw new IllegalArgumentException("Illegal pattern (Unclosed " + c + "): " + value);
                    char d = value.charAt(y);
                    if (d == '\\') {
                        if (y == value.length() - 1)
                            throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                        key.append(value.charAt(++y));
                    } else if (c == '{' && d == '}') {
                        x = y;
                        break;
                    } else if (c == '\'' && d == '\'') {
                        x = y;
                        break;
                    } else
                        key.append(d);
                }
                if (c == '\'')
                    buf.append(key);
                else {
                    Object v = tokens.get(key.toString());
                    if (v instanceof Supplier)
                        v = ((Supplier<?>) v).get();

                    buf.append(v == null ? "{" + key + "}" : v);
                }
            } else {
                buf.append(c);
            }
        }

        return buf.toString();
    }

    public static int getMappingSeparatorIdx(String mapping) {
        if (!mapping.contains("23w13a_or_b"))
            return mapping.lastIndexOf('_');

        // Just assume we will never have a mapping channel that has an underscore along with "23w13a_or_b"
        return mapping.indexOf('_');
    }

    public static void setupIDEResourceCopy(@Nonnull final Project project) {
        boolean ideaFound = true;
        if (project.getPlugins().hasPlugin(IdeaPlugin.class)) {
            final IdeaPlugin idea = project.getPlugins().getPlugin(IdeaPlugin.class);
            project.getTasks().register(CopyIntellijResources.NAME, CopyIntellijResources.class, task -> task.configure(idea.getModel(), project));
        } else {
            ideaFound = false;
        }

        if (project.getPlugins().hasPlugin(EclipsePlugin.class)) {
            final TaskProvider<CopyEclipseResources> taskProvider = project.getTasks().register(CopyEclipseResources.NAME, CopyEclipseResources.class, task -> task.dependsOn("eclipse"));
            project.getTasks().named("eclipse").configure(eclipseTask -> eclipseTask.doLast(eclTask -> {
                final EclipseModel eclipse = project.getExtensions().findByType(EclipseModel.class);
                // The `eclipse` task has been run, the only way the model would be null is if something has gone seriously wrong
                taskProvider.get().configure(Objects.requireNonNull(eclipse), project);
            }));
        } else if (!ideaFound) {
            project.getLogger().warn("Neither the 'eclipse' nor the 'idea' plugins were found, but IDE resource copy has been enabled.");
        }
    }

    public static String getIntellijOutName(@Nonnull final SourceSet sourceSet) {
        return sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : sourceSet.getName();
    }
}

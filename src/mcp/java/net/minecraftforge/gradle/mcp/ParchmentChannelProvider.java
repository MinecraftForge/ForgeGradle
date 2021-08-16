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

package net.minecraftforge.gradle.mcp;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraftforge.gradle.common.config.MCPConfigV2;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IClass;
import net.minecraftforge.srgutils.IMappingFile.INode;
import net.minecraftforge.srgutils.IRenamer;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class ParchmentChannelProvider implements ChannelProvider {
    private static final Gson GSON = new Gson();
    private static final Pattern PARCHMENT_PATTERN = Pattern.compile("(?<mappingsversion>[\\w\\-.]+)-(?<mcpversion>(?<mcversion>[\\d.]+)(?:-\\d{8}\\.\\d{6})?)");
    private static final Pattern LETTERS_ONLY_PATTERN = Pattern.compile("[a-zA-Z]+");

    @Nonnull
    @Override
    public Set<String> getChannels() {
        return ImmutableSet.of("parchment");
    }

    @Nullable
    @Override
    public File getMappingsFile(MCPRepo mcpRepo, Project project, String channel, String version) throws IOException {
        // Format is {MAPPINGS_VERSION}-{MC_VERSION}-{MCP_VERSION} where MCP_VERSION is optional
        Matcher matcher = PARCHMENT_PATTERN.matcher(version);
        if (!matcher.matches())
            throw new IllegalStateException("Parchment version of " + version + " is invalid");

        String mappingsversion = matcher.group("mappingsversion");
        String mcversion = matcher.group("mcversion");
        String mcpversion = matcher.group("mcpversion");

        File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + mcversion + ":mappings@txt", true);
        if (client == null)
            throw new IllegalStateException("Could not create " + mcversion + " official mappings due to missing ProGuard mappings.");

        File tsrg = mcpRepo.findRenames("obf_to_srg", IMappingFile.Format.TSRG2, mcpversion, false);
        if (tsrg == null)
            throw new IllegalStateException("Could not create " + mcpversion + " parchment mappings due to missing MCP's tsrg");

        File mcp = mcpRepo.getMCP(mcpversion);
        if (mcp == null)
            return null;

        String artifact = "org.parchmentmc.data:parchment-" + mcversion + ":" + mappingsversion + ":checked@zip";
        File dep = MavenArtifactDownloader.manual(project, artifact, false);
        if (dep == null) {
            // TODO remove this later? or keep backwards-compatibility with older releases?
            dep = MavenArtifactDownloader.manual(project, artifact.replace(":checked", ""), false);
        }
        if (dep == null)
            throw new IllegalStateException("Could not find Parchment version of " + mappingsversion + '-' + mcversion + " with artifact " + artifact);

        File mappings = cacheParchment(mcpRepo, mcpversion, mappingsversion, "zip");
        HashStore cache = mcpRepo.commonHash(mcp)
                .load(cacheParchment(mcpRepo, mcpversion, mappingsversion, "zip.input"))
                .add("mcversion", version)
                .add("mappings", dep)
                .add("tsrg", tsrg)
                .add("codever", "1");

        if (cache.isSame() && mappings.exists())
            return mappings;

        boolean official = MCPConfigV2.getFromArchive(mcp).isOfficial();
        try (ZipFile zip = new ZipFile(dep)) {
            ZipEntry entry = zip.getEntry("parchment.json");
            if (entry == null)
                throw new IllegalStateException("Parchment zip did not contain \"parchment.json\"");

            JsonObject json = GSON.fromJson(new InputStreamReader(zip.getInputStream(entry)), JsonObject.class);
            String specversion = json.get("version").getAsString();
            if (!specversion.startsWith("1."))
                throw new IllegalStateException("Parchment mappings spec version was " + specversion + " and did not start with \"1.\", cannot parse!");
            IMappingFile srg = IMappingFile.load(tsrg);
            IMappingFile mojToObf = IMappingFile.load(client);
            // Have to do it this way to preserve parameters and eliminate SRG classnames
            IMappingFile mojToSrg = srg.reverse().chain(mojToObf.reverse()).reverse().rename(new IRenamer() {
                @Override
                public String rename(IClass value) {
                    return value.getOriginal();
                }
            });

            String[] header = {"searge", "name", "desc"};
            List<String[]> packages = Lists.<String[]>newArrayList(header);
            List<String[]> classes = Lists.<String[]>newArrayList(header);
            List<String[]> fields = Lists.<String[]>newArrayList(header);
            List<String[]> methods = Lists.<String[]>newArrayList(header);
            List<String[]> parameters = Lists.<String[]>newArrayList(header);

            Map<String, JsonObject> classMap = getNamedJsonMap(json.getAsJsonArray("classes"), false);
            Map<String, JsonObject> packageMap = getNamedJsonMap(json.getAsJsonArray("packages"), false);

            for (IMappingFile.IPackage srgPackage : mojToSrg.getPackages()) {
                JsonObject pckg = packageMap.get(srgPackage.getOriginal());
                populateMappings(packages, null, srgPackage, pckg);
            }
            for (IClass srgClass : mojToSrg.getClasses()) {
                JsonObject cls = classMap.get(srgClass.getOriginal());
                populateMappings(classes, srgClass, srgClass, cls);

                Map<String, JsonObject> fieldMap = cls == null ? ImmutableMap.of() : getNamedJsonMap(cls.getAsJsonArray("fields"), false);
                for (IMappingFile.IField srgField : srgClass.getFields()) {
                    populateMappings(fields, srgClass, srgField, fieldMap.get(srgField.getOriginal()));
                }

                Map<String, JsonObject> methodMap = cls == null ? ImmutableMap.of() : getNamedJsonMap(cls.getAsJsonArray("methods"), true);
                for (IMappingFile.IMethod srgMethod : srgClass.getMethods()) {
                    JsonObject method = methodMap.get(srgMethod.getOriginal() + srgMethod.getDescriptor());
                    StringBuilder mdJavadoc = new StringBuilder(getJavadocs(method));
                    List<IMappingFile.IParameter> srgParams = new ArrayList<>(srgMethod.getParameters());
                    if (method != null && method.has("parameters")) {
                        JsonArray jsonParams = method.getAsJsonArray("parameters");
                        if (!official || jsonParams.size() == srgParams.size())
                            for (int i = 0; i < jsonParams.size(); i++) {
                                JsonObject parameter = jsonParams.get(i).getAsJsonObject();
                                boolean isConstructor = method.get("name").getAsString().equals("<init>");
                                String srgParam;
                                if (official) {
                                    srgParam = srgParams.get(i).getMapped();
                                } else {
                                    String srgId = srgMethod.getMapped().indexOf('_') == -1
                                            ? srgMethod.getMapped()
                                            : srgMethod.getMapped().split("_")[1];
                                    if (LETTERS_ONLY_PATTERN.matcher(srgId).matches())
                                        continue; // This means it's a mapped parameter of a functional interface method and we can't use it.
                                    srgParam = String.format(isConstructor ? "p_i%s_%s_" : "p_%s_%s_", srgId, parameter.get("index").getAsString());
                                }
                                String paramName = parameter.has("name") ? parameter.get("name").getAsString() : null;
                                if (paramName != null) {
                                    parameters.add(new String[]{srgParam, paramName, ""});
                                }
                                String paramJavadoc = getJavadocs(parameter);
                                if (!paramJavadoc.isEmpty())
                                    mdJavadoc.append("\\n@param ").append(paramName != null ? paramName : srgParam).append(' ').append(paramJavadoc);
                            }
                    }
                    populateMappings(methods, srgClass, srgMethod, mdJavadoc.toString());
                }
            }

            if (!mappings.getParentFile().exists())
                mappings.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(mappings);
                    ZipOutputStream out = new ZipOutputStream(fos)) {
                MCPRepo.writeCsv("classes.csv", classes, out);
                MCPRepo.writeCsv("fields.csv", fields, out);
                MCPRepo.writeCsv("methods.csv", methods, out);
                MCPRepo.writeCsv("params.csv", parameters, out);
                MCPRepo.writeCsv("packages.csv", packages, out);
            }
        }

        cache.save();
        Utils.updateHash(mappings, HashFunction.SHA1);

        return mappings;
    }

    private File cacheParchment(MCPRepo mcpRepo, String mcpversion, String mappingsVersion, String ext) {
        return mcpRepo.cache("org", "parchmentmc", "data", "parchment-" + mcpversion, mappingsVersion, "parchment-" + mcpversion + '-' + mappingsVersion + '.' + ext);
    }

    private Map<String, JsonObject> getNamedJsonMap(JsonArray array, boolean hasDescriptor) {
        if (array == null || array.size() == 0)
            return ImmutableMap.of();
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonObject.class::cast)
                .collect(Collectors.toMap(j -> {
                    String key = j.get("name").getAsString();
                    if (hasDescriptor)
                        key += j.get("descriptor").getAsString();
                    return key;
                }, Functions.identity()));
    }

    private void populateMappings(List<String[]> mappings, IClass srgClass, INode srgNode, JsonObject json) {
        populateMappings(mappings, srgClass, srgNode, getJavadocs(json));
    }

    private void populateMappings(List<String[]> mappings, IClass srgClass, INode srgNode, String desc) {
        if (srgNode instanceof IMappingFile.IPackage || srgNode instanceof IClass) {
            String name = srgNode.getMapped().replace('/', '.');
            // TODO fix InstallerTools so that we don't have to expand the csv size for no reason
            if (!desc.isEmpty())
                mappings.add(new String[]{name, name, desc});
            return;
        }
        String srgName = srgNode.getMapped();
        String mojName = srgNode.getOriginal();
        boolean isSrg = srgName.startsWith("p_") || srgName.startsWith("func_") || srgName.startsWith("m_") || srgName.startsWith("field_") || srgName.startsWith("f_");
        // If it's not a srg id and has javadocs, we need to add the class to the beginning as it is a special method/field of some kind
        if (!isSrg && !desc.isEmpty() && (srgNode instanceof IMappingFile.IMethod || srgNode instanceof IMappingFile.IField)) {
            srgName = srgClass.getMapped().replace('/', '.') + '#' + srgName;
        }
        // Only add to the mappings list if it is mapped or has javadocs
        if ((isSrg && !srgName.equals(mojName)) || !desc.isEmpty())
            mappings.add(new String[]{srgName, mojName, desc});
    }

    private String getJavadocs(JsonObject json) {
        if (json == null)
            return "";
        JsonElement element = json.get("javadoc");
        if (element == null)
            return "";
        if (element instanceof JsonPrimitive)
            return element.getAsString(); // Parameters don't use an array for some reason
        if (!(element instanceof JsonArray))
            return "";
        JsonArray array = (JsonArray) element;
        StringBuilder sb = new StringBuilder();
        int size = array.size();
        for (int i = 0; i < size; i++) {
            sb.append(array.get(i).getAsString());
            if (i != size - 1)
                sb.append("\\n");
        }
        return sb.toString();
    }
}

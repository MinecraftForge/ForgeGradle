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

import com.google.common.collect.ImmutableSet;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.srgutils.IMappingFile;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipOutputStream;

class OfficialChannelProvider implements ChannelProvider {
    @Nonnull
    @Override
    public Set<String> getChannels() {
        return ImmutableSet.of("official");
    }

    @Nullable
    @Override
    public File getMappingsFile(MCPRepo mcpRepo, Project project, String channel, String version) throws IOException {
        String mcpversion = version;
        int idx = version.lastIndexOf('-');
        if (idx != -1 && MinecraftRepo.MCP_CONFIG_VERSION.matcher(version.substring(idx + 1)).matches()) {
            //mcpversion = version.substring(idx);
            version = version.substring(0, idx);
        }
        File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + version + ":mappings@txt", true);
        File server = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + version + ":mappings@txt", true);
        if (client == null || server == null)
            throw new IllegalStateException("Could not create " + mcpversion + " official mappings due to missing ProGuard mappings.");

        File tsrg = mcpRepo.findRenames("obf_to_srg", IMappingFile.Format.TSRG, mcpversion, false);
        if (tsrg == null)
            throw new IllegalStateException("Could not create " + mcpversion + " official mappings due to missing MCP's tsrg");

        File mcp = mcpRepo.getMCP(mcpversion);
        if (mcp == null)
            return null;

        File mappings = mcpRepo.cacheMC("mapping", mcpversion, "mapping", "zip");
        HashStore cache = mcpRepo.commonHash(mcp)
                .load(mcpRepo.cacheMC("mapping", mcpversion, "mapping", "zip.input"))
                .add("pg_client", client)
                .add("pg_server", server)
                .add("tsrg", tsrg)
                .add("codever", "2");

        if (!cache.isSame() || !mappings.exists()) {
            IMappingFile pg_client = IMappingFile.load(client);
            IMappingFile pg_server = IMappingFile.load(server);

            //Verify that the PG files merge, merge in MCPConfig, but doesn't hurt to double check here.
            //And if we don't we need to write a handler to spit out correctly sided info.

            IMappingFile srg = IMappingFile.load(tsrg);

            Map<String, String> cfields = new TreeMap<>();
            Map<String, String> sfields = new TreeMap<>();
            Map<String, String> cmethods = new TreeMap<>();
            Map<String, String> smethods = new TreeMap<>();

            for (IMappingFile.IClass cls : pg_client.getClasses()) {
                IMappingFile.IClass obf = srg.getClass(cls.getMapped());
                if (obf == null) // Class exists in official source, but doesn't make it past obfusication so it's not in our mappings.
                    continue;
                for (IMappingFile.IField fld : cls.getFields()) {
                    String name = obf.remapField(fld.getMapped());
                    if (name.startsWith("field_") || name.startsWith("f_"))
                        cfields.put(name, fld.getOriginal());
                }
                for (IMappingFile.IMethod mtd : cls.getMethods()) {
                    String name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                    if (name.startsWith("func_") || name.startsWith("m_"))
                        cmethods.put(name, mtd.getOriginal());
                }
            }
            for (IMappingFile.IClass cls : pg_server.getClasses()) {
                IMappingFile.IClass obf = srg.getClass(cls.getMapped());
                if (obf == null) // Class exists in official source, but doesn't make it past obfusication so it's not in our mappings.
                    continue;
                for (IMappingFile.IField fld : cls.getFields()) {
                    String name = obf.remapField(fld.getMapped());
                    if (name.startsWith("field_") || name.startsWith("f_"))
                        sfields.put(name, fld.getOriginal());
                }
                for (IMappingFile.IMethod mtd : cls.getMethods()) {
                    String name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                    if (name.startsWith("func_") || name.startsWith("m_"))
                        smethods.put(name, mtd.getOriginal());
                }
            }

            String[] header = new String[] {"searge", "name", "side", "desc"};
            List<String[]> fields = new ArrayList<>();
            List<String[]> methods = new ArrayList<>();
            fields.add(header);
            methods.add(header);

            for (String name : cfields.keySet()) {
                String cname = cfields.get(name);
                String sname = sfields.get(name);
                if (cname.equals(sname)) {
                    fields.add(new String[]{name, cname, "2", ""});
                    sfields.remove(name);
                } else
                    fields.add(new String[]{name, cname, "0", ""});
            }

            for (String name : cmethods.keySet()) {
                String cname = cmethods.get(name);
                String sname = smethods.get(name);
                if (cname.equals(sname)) {
                    methods.add(new String[]{name, cname, "2", ""});
                    smethods.remove(name);
                } else
                    methods.add(new String[]{name, cname, "0", ""});
            }

            sfields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
            smethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));

            if (!mappings.getParentFile().exists())
                mappings.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(mappings);
                    ZipOutputStream out = new ZipOutputStream(fos)) {
                MCPRepo.writeCsv("fields.csv", fields, out);
                MCPRepo.writeCsv("methods.csv", methods, out);
            }

            cache.save();
            Utils.updateHash(mappings, HashFunction.SHA1);
        }


        return mappings;
    }
}

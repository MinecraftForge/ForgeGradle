package net.minecraftforge.gradle.common.mapping.provider;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.gradle.api.Project;
import net.minecraftforge.gradle.common.mapping.IMappingProvider;
import net.minecraftforge.gradle.common.mapping.detail.MappingDetails;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.mapping.IMappingInfo;
import net.minecraftforge.srgutils.IMappingFile;

import static net.minecraftforge.gradle.common.mapping.util.CacheUtils.*;

public class OfficialMappingProvider implements IMappingProvider {

    public static String OFFICIAL_CHANNEL = "official";

    @Override
    public Collection<String> getMappingChannels() {
        return Collections.singleton(OFFICIAL_CHANNEL);
    }

    @Override
    public IMappingInfo getMappingInfo(Project project, String channel, String version) throws IOException {
        String mcVersion = getMinecraftVersion(version);

        File clientPG = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + mcVersion + ":mappings@txt", true);
        File serverPG = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + mcVersion + ":mappings@txt", true);
        if (clientPG == null || serverPG == null)
            throw new IllegalStateException("Could not create " + version + " official mappings due to missing ProGuard mappings.");

        File tsrgFile = findRenames(project, "obf_to_srg", IMappingFile.Format.TSRG, version, false);
        if (tsrgFile == null)
            throw new IllegalStateException("Could not create " + version + " official mappings due to missing MCP's tsrg");

        File mcp = getMCPConfigZip(project, version);
        if (mcp == null) // TODO: handle when MCPConfig zip could not be downloaded
            throw new IllegalStateException("Could not create " + version + " official mappings due to missing MCPConfig zip");

        //TODO: Timeout / Abstract the License system? We want to run the display here so if it's used else where it still shows
        // MojangLicenseHelper.displayWarning(project, OFFICIAL_CHANNEL, version);

        File mappings = cacheMappings(project, channel, version, "zip");
        HashStore cache = commonHash(project, mcp)
            .load(cacheMappings(project, channel, version, "zip.input"))
            .add("pg_client", clientPG)
            .add("pg_server", serverPG)
            .add("tsrg", tsrgFile)
            .add("codever", "1");

        return fromCachable(channel, version, cache, mappings, () -> {
            // PG file: [MAP->OBF]
            IMappingFile pgClient = IMappingFile.load(clientPG);
            IMappingFile pgServer = IMappingFile.load(serverPG);

            // MCPConfig TSRG file: [OBF->SRG]
            IMappingFile tsrg = IMappingFile.load(tsrgFile);

            // Official: [SRG->MAP]
            //   Note: We chain off the tsrg so that we don't pick up none srg names
            //   [OBF->SRG] -reverse->            => [SRG->OBF]
            //   [MAP->OBF] -reverse->            => [OBF->MAP]
            //   [SRG->OBF] --chain--> [OBF->MAP] => [SRG->MAP]
            IMappingFile client = tsrg.reverse().chain(pgClient.reverse());
            IMappingFile server = tsrg.reverse().chain(pgServer.reverse());

            return MappingDetails.fromSrg(client, server);
        });
    }

    public static String getMinecraftVersion(String version) {
        int idx = version.lastIndexOf('-');

        if (idx != -1 && version.substring(idx + 1).matches("\\d{8}\\.\\d{6}")) {
            // The regex matches a timestamp attached to the version, like 1.16.5-20210101.010101
            // This removes the timestamp part, so mcVersion only contains the minecraft version (for getting the mappings)
            return version.substring(0, idx);
        }
        return version;
    }

    @Override
    public String toString() {
        return "Official Mappings";
    }
}
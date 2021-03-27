package net.minecraftforge.gradle.common.mapping.info;

import java.io.File;
import java.io.IOException;

import net.minecraftforge.gradle.common.mapping.detail.MappingDetails;
import net.minecraftforge.gradle.common.util.func.IOSupplier;
import net.minecraftforge.gradle.common.mapping.IMappingDetail;
import net.minecraftforge.gradle.common.mapping.IMappingInfo;

public class MappingInfo implements IMappingInfo {
    protected final String channel;
    protected final String version;
    protected final File destination;
    protected final IOSupplier<IMappingDetail> detail;

    protected MappingInfo(String channel, String version, File destination, IOSupplier<IMappingDetail> detail) {
        this.channel = channel;
        this.version = version;
        this.destination = destination;
        this.detail = detail;
    }

    @Override
    public String getChannel() {
        return channel;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public File get() {
        return destination;
    }

    @Override
    public IMappingDetail getDetails() throws IOException {
        return detail.get();
    }

    public static MappingInfo of(String channel, String version, File destination) {
        return of(channel, version, destination, () -> MappingDetails.fromZip(destination));
    }

    public static MappingInfo of(String channel, String version, File destination, IMappingDetail detail) {
        return of(channel, version, destination, () -> detail);
    }

    public static MappingInfo of(String channel, String version, File destination, IOSupplier<IMappingDetail> detail) {
        return new MappingInfo(channel, version, destination, detail);
    }
}
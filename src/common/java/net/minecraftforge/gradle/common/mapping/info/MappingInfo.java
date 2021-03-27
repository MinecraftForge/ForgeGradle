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
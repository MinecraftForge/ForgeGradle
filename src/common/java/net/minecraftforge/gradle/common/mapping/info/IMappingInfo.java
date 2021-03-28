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
import java.util.function.Supplier;

import net.minecraftforge.gradle.common.mapping.detail.IMappingDetail;
import net.minecraftforge.gradle.common.mapping.detail.MappingDetails;
import net.minecraftforge.gradle.common.util.func.IOSupplier;

/**
 * A resolved `mapping.zip`
 * @see MappingInfo
 */
public interface IMappingInfo extends Supplier<File> {
    /**
     * @return The channel used to generate/provide this IMappingInfo
     */
    String getChannel();

    /**
     * @return The version used to generate/provide this IMappingInfo
     */
    String getVersion();

    /**
     * @return The location of the `mappings.zip`, considered guaranteed to exist.
     */
    @Override
    File get();

    /**
     * @return A representation of the `mappings.zip` in an easy to manipulate format
     */
    IMappingDetail getDetails() throws IOException;

    static IMappingInfo of(String channel, String version, File destination) {
        return of(channel, version, destination, () -> MappingDetails.fromZip(destination));
    }

    static IMappingInfo of(String channel, String version, File destination, IMappingDetail detail) {
        return of(channel, version, destination, () -> detail);
    }

    static IMappingInfo of(String channel, String version, File destination, IOSupplier<IMappingDetail> detail) {
        return new MappingInfo(channel, version, destination, detail);
    }
}

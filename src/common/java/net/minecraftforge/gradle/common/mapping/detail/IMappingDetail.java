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

package net.minecraftforge.gradle.common.mapping.detail;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraftforge.gradle.common.mapping.util.Sides;
import net.minecraftforge.gradle.common.mapping.generator.MappingZipGenerator;
import net.minecraftforge.srgutils.IMappingFile;

/**
 * A Collection of maps of `SRG NAME` -> {@link INode} <br>
 * {@link MappingZipGenerator} takes an instance of this and generates a `mappings.zip` compatible with ForgeGradle
 * @see MappingDetail
 * @see MappingDetails
 */
public interface IMappingDetail {

    Map<String, INode> getClasses();

    Map<String, INode> getFields();

    Map<String, INode> getMethods();

    Map<String, INode> getParameters();

    interface INode {
        String getOriginal();

        String getMapped();

        /**
         * @see Sides
         */
        String getSide();

        String getJavadoc();

        INode withMapping(String mapped);

        INode withSide(String side);

        INode withJavadoc(String javadoc);

        static INode or(String key, @Nullable IMappingDetail.INode node) {
            return node != null ? node : of(key, key, Sides.BOTH, "");
        }

        static INode of(IMappingFile.INode node) {
            Map<String, String> meta = node.getMetadata();
            String side = meta.getOrDefault("side", Sides.BOTH);
            String javadoc = meta.getOrDefault("comment", ""); //TODO: Check that `comment` is the right key

            return of(node.getOriginal(), node.getMapped(), side, javadoc);
        }

        static INode of(String original, String mapped, String side, String javadoc) {
            return new Node(original, mapped, side, javadoc);
        }
    }

    static IMappingDetail of(Map<String, INode> classes, Map<String, INode> fields, Map<String, INode> methods, Map<String, INode> params) {
        return new MappingDetail(classes, fields, methods, params);
    }
}
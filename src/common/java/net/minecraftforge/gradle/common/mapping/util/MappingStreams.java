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

package net.minecraftforge.gradle.common.mapping.util;

import java.util.stream.Stream;

import net.minecraftforge.srgutils.IMappingFile;

/**
 * Utility Class to create {@link Stream}s from an {@link IMappingFile}
 */
public class MappingStreams {
    public static Stream<? extends IMappingFile.IClass> classes(IMappingFile mappings) {
        return mappings.getClasses().stream();
    }

    public static Stream<? extends IMappingFile.IField> fields(IMappingFile mappings) {
        return classes(mappings).flatMap(MappingStreams::fields);
    }

    public static Stream<? extends IMappingFile.IField> fields(IMappingFile.IClass cls) {
        return cls.getFields().stream();
    }

    public static Stream<? extends IMappingFile.IMethod> methods(IMappingFile mappings) {
        return classes(mappings).flatMap(MappingStreams::methods);
    }

    public static Stream<? extends IMappingFile.IMethod> methods(IMappingFile.IClass cls) {
        return cls.getMethods().stream();
    }

    public static Stream<? extends IMappingFile.IParameter> parameters(IMappingFile mappings) {
        return classes(mappings).flatMap(MappingStreams::methods).flatMap(MappingStreams::parameters);
    }

    public static Stream<? extends IMappingFile.IParameter> parameters(IMappingFile.IMethod mtd) {
        return mtd.getParameters().stream();
    }
}
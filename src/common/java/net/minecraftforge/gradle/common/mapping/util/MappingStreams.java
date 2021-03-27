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
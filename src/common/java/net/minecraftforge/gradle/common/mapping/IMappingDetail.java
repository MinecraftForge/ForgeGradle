package net.minecraftforge.gradle.common.mapping;

import java.util.Map;

import net.minecraftforge.gradle.common.mapping.detail.MappingDetail;
import net.minecraftforge.gradle.common.mapping.detail.MappingDetails;
import net.minecraftforge.gradle.common.mapping.generator.MappingZipGenerator;

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
    }
}
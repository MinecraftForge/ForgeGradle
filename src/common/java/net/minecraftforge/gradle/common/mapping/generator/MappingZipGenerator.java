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

package net.minecraftforge.gradle.common.mapping.generator;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.zip.ZipOutputStream;

import com.google.common.base.Preconditions;
import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.LineDelimiter;
import de.siegmar.fastcsv.writer.QuoteStrategy;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.mapping.IMappingDetail;

import static net.minecraftforge.gradle.common.mapping.detail.MappingDetails.encodeClass;
import static net.minecraftforge.gradle.common.mapping.detail.MappingDetails.encodeJavadoc;

public class MappingZipGenerator {

    /**
     * Generates a ForgeGradle compatible `mappings.zip` from an {@link IMappingDetail}
     */
    public static void generate(File output, IMappingDetail mappings) throws IOException {
        Preconditions.checkArgument(!output.exists() || output.isFile(), "Output zip must be a file");
        if (output.exists() && !output.delete()) {
            throw new IOException("Could not delete existing file " + output);
        }

        if (output.getParentFile() != null && !output.getParentFile().exists())
            //noinspection ResultOfMethodCallIgnored
            output.getParentFile().mkdirs();

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(output));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zip))) {
            Supplier<CsvWriter> supplier
                = () -> CsvWriter.builder()
                .quoteStrategy(QuoteStrategy.REQUIRED)
                .lineDelimiter(LineDelimiter.LF)
                .build(new UnclosingWriter(writer));

            // Classes
            writeCsvFile(supplier, zip, "classes.csv", mappings.getClasses(), MappingZipGenerator::isClassSrg);

            // Methods
            writeCsvFile(supplier, zip, "methods.csv", mappings.getMethods(), MappingZipGenerator::isMethodSrg);

            // Fields
            writeCsvFile(supplier, zip, "fields.csv", mappings.getFields(), MappingZipGenerator::isFieldSrg);

            // Parameters
            writeCsvFile(supplier, zip, "params.csv", mappings.getParameters(), MappingZipGenerator::isParamSrg);
        }
    }

    private static final Comparator<IMappingDetail.INode> BY_ORIGINAL = Comparator.comparing(IMappingDetail.INode::getOriginal);

    public static void writeCsvFile(Supplier<CsvWriter> writer, ZipOutputStream zipOut, String fileName, Map<String, IMappingDetail.INode> input, Predicate<IMappingDetail.INode> predicate) throws IOException {
        Iterator<IMappingDetail.INode> nodes = input.values().stream().sorted(BY_ORIGINAL).filter(predicate).iterator();

        if (nodes.hasNext()) {
            zipOut.putNextEntry(Utils.getStableEntry(fileName));

            try (CsvWriter csv = writer.get()) {
                csv.writeRow(fileName.equals("params.csv") ? "param" : "searge", "name", "side", "desc");

                nodes.forEachRemaining(node ->
                    csv.writeRow(encodeClass(node.getOriginal()), encodeClass(node.getMapped()), node.getSide(), encodeJavadoc(node.getJavadoc()))
                );
            }

            zipOut.closeEntry();
        }
    }

    private static boolean isClassSrg(IMappingDetail.INode node) {
        String original = node.getOriginal();
        return original.startsWith("net/minecraft/src/C_") || !node.getJavadoc().isEmpty();
    }

    private static boolean isFieldSrg(IMappingDetail.INode node) {
        String original = node.getOriginal();
        return original.startsWith("field_") || original.startsWith("f_") || !node.getJavadoc().isEmpty();
    }

    private static boolean isMethodSrg(IMappingDetail.INode node) {
        String original = node.getOriginal();
        return original.startsWith("func_") || original.startsWith("m_") || !node.getJavadoc().isEmpty();
    }

    private static boolean isParamSrg(IMappingDetail.INode node) {
        String original = node.getOriginal();
        return original.startsWith("p_") || !node.getJavadoc().isEmpty();
    }
}
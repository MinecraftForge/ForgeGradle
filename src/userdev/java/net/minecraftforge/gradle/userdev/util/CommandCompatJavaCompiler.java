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

package net.minecraftforge.gradle.userdev.util;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.IOUtils;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

class CommandCompatJavaCompiler extends AbstractCompatJavaCompiler {

    private final Logger logger;
    private final String javacExecutable;

    CommandCompatJavaCompiler(Logger logger, String javacExecutable) {
        this.logger = logger;
        this.javacExecutable = javacExecutable;
    }

    @Override
    public void compile() {
        try {
            List<String> arguments = buildArguments();
            logger.info("Starting compilation, command={} {}",
                javacExecutable, String.join(" ", arguments));
            Path argFile = writeArgFile(arguments);
            Path log = Files.createTempFile("ForgeGradle-javac", ".log");
            Process process = new ProcessBuilder(
                javacExecutable, "@" + argFile.toAbsolutePath().toString()
            )
                .redirectOutput(log.toFile())
                .redirectErrorStream(true)
                .directory(getDestinationDir())
                .start();
            process.getOutputStream().close();
            int exitCode;
            try {
                exitCode = process.waitFor();
            } finally {
                copyLog(log);
                Files.deleteIfExists(argFile);
            }
            if (exitCode != 0) {
                throw new IllegalStateException("Compilation failed, see log for details");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private Path writeArgFile(List<String> arguments) throws IOException {
        Path argFile = Files.createTempFile("ForgeGradle-javac", ".argfile");
        List<String> argumentLines = arguments.stream()
            .map(argument -> {
                if (argument.contains(" ")) {
                    // Quote spaces, escape backslashes
                    return "\"" + argument.replace("\\", "\\\\") + "\"";
                }
                return argument;
            })
            .collect(Collectors.toList());
        Files.write(argFile, argumentLines);
        return argFile;
    }

    private void copyLog(Path log) throws IOException {
        try (InputStream in = Files.newInputStream(log);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8.newDecoder()
                 .onUnmappableCharacter(CodingErrorAction.REPLACE)
                 .onMalformedInput(CodingErrorAction.REPLACE))) {
            IOUtils.lineIterator(reader).forEachRemaining(line -> {
                if (isSuppressedLine(line)) {
                    logger.debug(line);
                } else {
                    logger.info(line);
                }
            });
        }
    }

    private List<String> buildArguments() {
        return new ImmutableList.Builder<String>()
            .addAll(createOptions())
            .addAll(getSource().getFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList()))
            .build();
    }
}

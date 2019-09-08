/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.minecraftforge.gradle.common.util;

import com.google.common.base.Splitter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Minimized copy of Gradle's Jvm class.
 *
 * Forge edits:
 * - Use no internal Gradle API
 * - Use new Path API
 */
public class Jvm {

    public static Path findJavacExecutable() {
        Path javaHome = findJavaHome();
        String javacExecutableName = getExecutableName("javac");
        Path javac = javaHome.resolve("bin").resolve(javacExecutableName);
        if (Files.exists(javac)) {
            return javac;
        }
        for (String path : Splitter.on(File.pathSeparatorChar).split(System.getenv("PATH"))) {
            javac = Paths.get(path, javacExecutableName);
            if (Files.exists(javac)) {
                return javac;
            }
        }
        return null;
    }

    private static String getExecutableName(String name) {
        return Utils.isWindows() && !name.endsWith(".exe") ? name + ".exe" : name;
    }

    private static Path findJavaHome() {
        List<Path> javaHomeCandidates = Stream.of(
            System.getenv("JAVA_HOME"), System.getProperty("java.home")
        )
            .filter(Objects::nonNull)
            .filter(it -> !it.isEmpty())
            .map(Paths::get)
            .collect(Collectors.toList());
        if (javaHomeCandidates.isEmpty()) {
            // This should never happen, as java.home is a guaranteed property
            throw new IllegalStateException("No Java home candidates available");
        }
        return javaHomeCandidates.stream()
            .map(Jvm::findJavaHome)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> javaHomeCandidates.get(0));
    }

    private static Path findJavaHome(Path givenJavaHome) {
        Path toolsJar = findToolsJar(givenJavaHome);
        if (toolsJar != null) {
            return toolsJar.getParent().getParent();
        } else if (givenJavaHome.getFileName().toString().equalsIgnoreCase("jre")
            && Files.exists(givenJavaHome.resolveSibling("bin").resolve(getExecutableName("java")))) {
            return givenJavaHome.getParent();
        }
        return null;
    }

    private static Path findToolsJar(Path javaHome) {
        Path toolsJar = javaHome.resolve("lib/tools.jar");
        if (Files.exists(toolsJar)) {
            return toolsJar;
        }
        String dirName = javaHome.getFileName().toString();
        if (dirName.equalsIgnoreCase("jre")) {
            toolsJar = javaHome.resolveSibling("lib/tools.jar");
            if (Files.exists(toolsJar)) {
                return toolsJar;
            }
        }

        if (Utils.isWindows()) {
            String version = System.getProperty("java.version");
            if (dirName.matches("jre\\d+") || dirName.equals("jre" + version)) {
                toolsJar = javaHome.resolveSibling("jdk" + version).resolve("lib/tools.jar");
                if (Files.exists(toolsJar)) {
                    return toolsJar;
                }
            }
        }

        return null;
    }

}

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

import com.google.common.collect.Iterables;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.tools.JavaCompiler;
import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public class CompatJavaCompilerLoader {

    private static final Logger LOGGER = Logging.getLogger(CompatJavaCompilerLoader.class);
    private static final Function<Logger, CompatJavaCompiler> FACTORY = createCompilerFactory();

    private static Function<Logger, CompatJavaCompiler> createCompilerFactory() {
        try {
            return createJavaxToolsCompilerFactory();
        } catch (Exception e) {
            LOGGER.debug("Failed to load JavaxTools compiler, falling back to command", e);
            Path javacExecutablePath = Jvm.findJavacExecutable();
            requireNonNull(javacExecutablePath, "No javac found in JAVA_HOME or PATH. Please install a JDK.");
            String javacExecutable = javacExecutablePath.toAbsolutePath().toString();
            return logger -> new CommandCompatJavaCompiler(logger, javacExecutable);
        }
    }

    private static Function<Logger, CompatJavaCompiler> createJavaxToolsCompilerFactory() throws Exception {
        try {
            // try ServiceLoader
            Class<?> javaCompilerClass = Class.forName("javax.tools.JavaCompiler");
            Object javaCompiler = Iterables.getFirst(ServiceLoader.load(javaCompilerClass), null);
            if (javaCompiler != null) {
                return logger -> new JavaxToolsJavaCompiler(logger, (JavaCompiler) javaCompiler);
            }

            // try ToolProvider if no instances in ServiceLoader
            Class<?> toolsProviderClass = Class.forName("javax.tools.ToolProvider");
            Object providedJavaCompiler = toolsProviderClass.getDeclaredMethod("getSystemJavaCompiler").invoke(null);
            requireNonNull(providedJavaCompiler, "No Java compiler is provided by this platform");
            return logger -> new JavaxToolsJavaCompiler(logger, (JavaCompiler) providedJavaCompiler);
        } catch (Exception e) {
            // try direct instantiation of the relevant com.sun tool class
            try {
                Class<?> javacToolClass = Class.forName("com.sun.tools.javac.api.JavacTool");
                Object javaCompiler = javacToolClass.newInstance();
                return logger -> new JavaxToolsJavaCompiler(logger, (JavaCompiler) javaCompiler);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                ex.addSuppressed(e);
                throw ex;
            }
        }
    }

    public static CompatJavaCompiler getCompiler(Logger logger) {
        return FACTORY.apply(logger);
    }

    private CompatJavaCompilerLoader() {
    }
}

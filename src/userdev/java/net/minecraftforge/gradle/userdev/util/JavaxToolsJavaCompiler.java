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

import org.gradle.api.logging.Logger;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * Watered-down version of the JavaCompile task. Used to compile things
 * outside of Gradle's controls.
 */
class JavaxToolsJavaCompiler extends AbstractCompatJavaCompiler {

    private static final class Reporter implements DiagnosticListener<JavaFileObject> {

        private final Logger logger;

        private Reporter(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            String message = diagnostic.getMessage(null);
            if (isSuppressedLine(message)) {
                logger.debug(message);
                return;
            }
            switch (diagnostic.getKind()) {
                case ERROR:
                    logger.error(message);
                    break;
                case WARNING:
                case MANDATORY_WARNING:
                    logger.warn(message);
                    break;
                case NOTE:
                case OTHER:
                    logger.lifecycle(message);
                    break;
            }
        }
    }

    private final Logger logger;
    private final Reporter reporter;
    private final JavaCompiler compiler;

    JavaxToolsJavaCompiler(Logger logger, JavaCompiler compiler) {
        this.logger = logger;
        this.reporter = new Reporter(logger);
        this.compiler = compiler;
    }

    @Override
    public void compile() {
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(reporter, null, StandardCharsets.UTF_8);

        List<String> options = createOptions();
        Set<File> sourceFiles = requireNonNull(getSource()).getFiles();
        Iterable<? extends JavaFileObject> units = createUnits(fileManager, sourceFiles);

        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, reporter, options, null, units
        );
        logger.info("Starting compilation, compiler={}, sourceFiles={}, options={}",
            compiler.getClass().getName(), options, sourceFiles);
        if (!task.call()) {
            throw new IllegalStateException("Compilation failed, see logs for details.");
        }
    }

    private Iterable<? extends JavaFileObject> createUnits(StandardJavaFileManager fileManager, Set<File> sourceFiles) {
        return fileManager.getJavaFileObjectsFromFiles(sourceFiles);
    }

}

package net.minecraftforge.gradle.userdev.util;

import com.google.common.collect.ImmutableList;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.tasks.compile.JavaHomeBasedJavaCompilerFactory;
import org.gradle.api.logging.Logger;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Watered-down version of the JavaCompile task. Used to compile things
 * outside of Gradle's controls.
 */
public class NonTaskJavaCompiler {

    private static final class Reporter implements DiagnosticListener<JavaFileObject> {

        private final Logger logger;

        private Reporter(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            String message = diagnostic.getMessage(null);
            if (message.startsWith("bootstrap class path not set in conjunction with")) {
                // use debug for this message, it's not relevant for MC compilation
                logger.debug(message);
                return;
            }
            switch (diagnostic.getKind()) {
                case ERROR: logger.error(message); break;
                case WARNING:
                case MANDATORY_WARNING: logger.warn(message); break;
                case NOTE:
                case OTHER: logger.lifecycle(message); break;
            }
        }
    }

    private final Logger logger;
    private final Reporter reporter;
    private FileCollection classpath;
    private JavaVersion sourceCompatibility = JavaVersion.current();
    private JavaVersion targetCompatibility = JavaVersion.current();
    private File destinationDir;
    private FileTree source;

    public NonTaskJavaCompiler(Logger logger) {
        this.logger = logger;
        this.reporter = new Reporter(logger);
    }

    public void compile() {
        JavaCompiler compiler = getJavaCompilerSafe();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(reporter, null, StandardCharsets.UTF_8);

        List<String> options = createOptions();
        Set<File> sourceFiles = requireNonNull(source).getFiles();
        Iterable<? extends JavaFileObject> units = createUnits(fileManager, sourceFiles);

        JavaCompiler.CompilationTask task = compiler.getTask(
            null, fileManager, reporter, options, null, units
        );
        logger.info("Starting compilation, compiler={}, sourceFiles={}, options={}",
            compiler.getClass().getName(), options, sourceFiles);
        if (!task.call()) {
            throw new RuntimeException("Compilation failed, see logs for details.");
        }
    }

    private JavaCompiler getJavaCompilerSafe() {
        try {
            return new JavaHomeBasedJavaCompilerFactory().create();
        } catch (Exception e) {
            // Perhaps Gradle internals were refactored. Use a simpler but maybe incorrect method.
            logger.info("Unable to get Java compiler via Gradle, using ToolProvider", e);
            return ToolProvider.getSystemJavaCompiler();
        }
    }

    private List<String> createOptions() {
        Set<File> cp = requireNonNull(classpath).getFiles();
        String sourceCompat = requireNonNull(sourceCompatibility).toString();
        String targetCompat = requireNonNull(targetCompatibility).toString();
        String destDir = requireNonNull(destinationDir).getAbsolutePath();

        ImmutableList.Builder<String> options = ImmutableList.builder();
        if (!cp.isEmpty()) {
            options.add("-cp");
            options.add(cp.stream().map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));
        }
        options.add("-source").add(sourceCompat);
        options.add("-target").add(targetCompat);
        options.add("-d").add(destDir);
        return options.build();
    }

    private Iterable<? extends JavaFileObject> createUnits(StandardJavaFileManager fileManager, Set<File> sourceFiles) {
        return fileManager.getJavaFileObjectsFromFiles(sourceFiles);
    }

    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    public JavaVersion getSourceCompatibility() {
        return sourceCompatibility;
    }

    public void setSourceCompatibility(JavaVersion sourceCompatibility) {
        this.sourceCompatibility = sourceCompatibility;
    }

    public JavaVersion getTargetCompatibility() {
        return targetCompatibility;
    }

    public void setTargetCompatibility(JavaVersion targetCompatibility) {
        this.targetCompatibility = targetCompatibility;
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public void setDestinationDir(File destinationDir) {
        this.destinationDir = destinationDir;
    }

    public FileTree getSource() {
        return source;
    }

    public void setSource(FileTree source) {
        this.source = source;
    }
}

package net.minecraftforge.gradle;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

final class MavenizerAction implements Future<RegularFile> {
    private static final Logger LOGGER = Logging.getLogger(MavenizerAction.class);

    final ConfigurableFileCollection classpath;
    final Property<JavaLauncher> javaLauncher;
    final Property<String> mainClass;
    final DirectoryProperty caches;
    final DirectoryProperty output;
    final Property<String> module;
    final Property<String> version;
    final Property<MinecraftMappings> mappings;

    private final CapturingLogger capturingLogger;
    private final CapturingLogger capturingError;
    private final CompletableFuture<RegularFile> future;

    private final ExecOperations execOperations;

    MavenizerAction(ObjectFactory objects, ExecOperations execOperations, Action<? super MavenizerAction> action) {
        this.execOperations = execOperations;

        this.classpath = objects.fileCollection();
        this.javaLauncher = objects.property(JavaLauncher.class);
        this.mainClass = objects.property(String.class);
        this.caches = objects.directoryProperty();
        this.output = objects.directoryProperty();
        this.module = objects.property(String.class);
        this.version = objects.property(String.class);
        this.mappings = objects.property(MinecraftMappings.class);

        this.capturingLogger = new CapturingLogger(LOGGER::lifecycle);
        this.capturingError = new CapturingLogger(LOGGER::error);

        action.execute(this);

        this.classpath.finalizeValue();
        this.javaLauncher.finalizeValue();
        this.mainClass.finalizeValue();
        this.caches.finalizeValue();
        this.output.finalizeValue();
        this.module.finalizeValue();
        this.version.finalizeValue();
        this.mappings.finalizeValue();

        this.future = CompletableFuture.supplyAsync(this::invoke);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return this.future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return this.future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return this.future.isDone();
    }

    @Override
    public RegularFile get() throws InterruptedException, ExecutionException {
        return this.future.get();
    }

    @Override
    public RegularFile get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return this.future.get(timeout, unit);
    }

    void releaseLog() {
        this.capturingLogger.release();
        this.capturingError.release();
    }

    private RegularFile invoke() {
        String group;
        String name;
        {
            var split = module.get().split(":");
            if (split.length != 2) {
                // TODO use problems API
                throw new IllegalArgumentException("Invalid Minecraft dependency module name: " + module.get());
            }
            group = split[0];
            name = split[1];
        }

        try (var stdOut = Util.toLog(this.capturingLogger);
             var stdErr = Util.toLog(this.capturingError)) {
            this.execOperations.javaexec(spec -> {
                spec.setStandardOutput(stdOut);
                spec.setErrorOutput(stdErr);

                spec.setClasspath(classpath);
                spec.setExecutable(javaLauncher.get().getExecutablePath());
                spec.getMainClass().set(mainClass);

                spec.setArgs(getArgs());
            }).rethrowFailure();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // TODO This assumes the placement of the metadata.zip file
        //      It might be better to specify a location to output it, rather than include it as an artifact
        return output.file(Util.artifactPath(group, name, version.get(), "metadata", "zip")).get();
    }

    private List<String> getArgs() {
        var args = new ArrayList<>(List.of(
            "--maven",
            "--cache", caches.get().getAsFile().getAbsolutePath(),
            "--output", output.get().getAsFile().getAbsolutePath(),
            "--jdk-cache", caches.dir("jdks").get().getAsFile().getAbsolutePath(),
            "--artifact", module.get(),
            "--version", version.get(),
            "--global-auxiliary-variants"
        ));

        if ("parchment".equals(mappings.get().channel())) {
            args.add("--parchment");
            args.add(mappings.get().version());
        }

        return args;
    }

    private static final class CapturingLogger implements Consumer<String> {
        private final Queue<String> lines = new ConcurrentLinkedQueue<>();
        private final Consumer<? super String> logger;
        private boolean capturing = true;

        private CapturingLogger(Consumer<? super String> logger) {
            this.logger = logger;
        }

        @Override
        public void accept(String s) {
            if (capturing) {
                lines.add(s);
            } else {
                logger.accept(s);
            }
        }

        private void release() {
            if (!capturing) return;

            lines.forEach(logger);
            capturing = false;
        }
    }
}

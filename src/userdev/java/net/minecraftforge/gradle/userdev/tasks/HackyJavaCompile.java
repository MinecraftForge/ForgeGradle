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

package net.minecraftforge.gradle.userdev.tasks;

import javax.annotation.Nullable;

import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultToolchainJavaCompiler;
import org.gradle.jvm.toolchain.internal.SpecificInstallationToolchainSpec;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/*
 *  A terrible hack to use JavaCompile while bypassing
 *  Gradle's normal task infrastructure.
 *  This is internal API Modderrs DO NOT referencee this.
 *  It can and will be removed if we get a better way to do this.
 */
public class HackyJavaCompile extends JavaCompile {

    @SuppressWarnings({"rawtypes", "unchecked"})
    public void doHackyCompile() {

        // What follows is a horrible hack to allow us to call JavaCompile
        // from our dependency resolver.
        // As described in https://github.com/MinecraftForge/ForgeGradle/issues/550,
        // invoking Gradle tasks in the normal way can lead to deadlocks
        // when done from a dependency resolver.

        this.getOutputs().setPreviousOutputFiles(this.getProject().files());

        final Clock clock = Time.clock();
        final LongSupplier supplier = clock::getCurrentTime;
        final FileSystem fileSystem = FileSystems.getDefault();
        final Predicate<? super File> isSymLink = fileSystem::isSymlink;
        final DefaultDeleter defaultDeleter = new DefaultDeleter(supplier, isSymLink, OperatingSystem.current().isWindows());

        final DefaultJavaCompileSpec spec;
        try {
            Method createSpec = JavaCompile.class.getDeclaredMethod("createSpec");
            createSpec.setAccessible(true);
            spec = (DefaultJavaCompileSpec) createSpec.invoke(this);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Exception calling createSpec ", e);
        }
        spec.setSourceFiles(getSource());
        Compiler<JavaCompileSpec> javaCompiler = createToolchainCompiler();
        CleaningJavaCompiler compiler = new CleaningJavaCompiler(javaCompiler, getOutputs(), defaultDeleter);
        final WorkResult execute = compiler.execute(spec);
        setDidWork(execute.getDidWork());
    }

    // Copied from JavaCompile#createToolchainCompiler
    private <T extends CompileSpec> Compiler<T> createToolchainCompiler() {
        return spec -> {
            final Provider<JavaCompiler> compilerProvider = getCompilerTool();
            final DefaultToolchainJavaCompiler compiler = (DefaultToolchainJavaCompiler) compilerProvider.get();
            return compiler.execute(spec);
        };
    }

    // Copied from JavaCompile#getCompilerTool
    private Provider<JavaCompiler> getCompilerTool() {
        JavaToolchainSpec explicitToolchain = determineExplicitToolchain();
        if(explicitToolchain == null) {
            if(getJavaCompiler().isPresent()) {
                return this.getJavaCompiler();
            } else {
                explicitToolchain = new CurrentJvmToolchainSpec(getProject().getObjects());
            }
        }
        return getJavaToolchainService().compilerFor(explicitToolchain);
    }

    // Copied from JavaCompile#determineExplicitToolchain
    @Nullable
    private JavaToolchainSpec determineExplicitToolchain() {
        final File customJavaHome = getOptions().getForkOptions().getJavaHome();
        if (customJavaHome != null) {
            return new SpecificInstallationToolchainSpec(getProject().getObjects(), customJavaHome);
        } else {
            final String customExecutable = getOptions().getForkOptions().getExecutable();
            if (customExecutable != null) {
                final File executable = new File(customExecutable);
                if(executable.exists()) {
                    return new SpecificInstallationToolchainSpec(getProject().getObjects(), executable.getParentFile().getParentFile());
                }
            }
        }
        return null;
    }
}

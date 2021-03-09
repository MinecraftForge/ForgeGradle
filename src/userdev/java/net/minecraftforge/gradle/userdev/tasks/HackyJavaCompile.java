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

import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;

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

    @SuppressWarnings({"rawtypes", "unchecked", "deprecation", "UnstableApiUsage"})
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
        Compiler<JavaCompileSpec> javaCompiler = CompilerUtil.castCompiler(((JavaToolChainInternal) getToolChain()).select(getPlatform()).newCompiler(spec.getClass()));
        CleaningJavaCompiler compiler = new CleaningJavaCompiler(javaCompiler, getOutputs(), defaultDeleter);
        final WorkResult execute = compiler.execute(spec);
        setDidWork(execute.getDidWork());
    }

}

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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.compile.CleaningJavaCompiler;
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.internal.toolchain.JavaToolChainInternal;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.language.base.internal.compile.CompilerUtil;
import org.gradle.util.GradleVersion;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// A terrible hack to use JavaCompile while bypassing
// Gradle's normal task infrastructure.
public class HackyJavaCompile extends JavaCompile {

    public void doHackyCompile() {

        // What follows is a horrible hack to allow us to call JavaCompile
        // from our dependency resolver.
        // As described in https://github.com/MinecraftForge/ForgeGradle/issues/550,
        // invoking Gradle tasks in the normal way can lead to deadlocks
        // when done from a dependency resolver.

        // To avoid these issues, we invoke the 'compile' method on JavaCompile
        // using reflection.

        // Normally, the output history is set by Gradle. Since we're bypassing
        // the normal gradle task infrastructure, we need to do it ourselves.

        // TaskExecutionHistory is removed in 5.1.0, so we only try to set it
        // on versions below 5.1.0

        if (GradleVersion.current().compareTo(GradleVersion.version("5.1.0")) < 0) {
            try {
                Class<?> taskExectionHistory = Class.forName("org.gradle.api.internal.TaskExecutionHistory");
                Method setHistory = this.getOutputs().getClass().getMethod("setHistory", taskExectionHistory);
                Object dummyHistory = Class.forName("net.minecraftforge.gradle.userdev.util.DummyTaskExecutionHistory").newInstance();
                setHistory.invoke(this.getOutputs(), dummyHistory);
            } catch (Exception e) {
                throw new RuntimeException("Exception calling setHistory", e);
            }

            // Do the actual compilation,
            // bypassing a bunch of Gradle's other stuff (e.g. internal event listener mechanism)
            this.compile();
        } else {
            try {
                Method setPreviousOutputFiles = this.getOutputs().getClass().getMethod("setPreviousOutputFiles", FileCollection.class);
                setPreviousOutputFiles.invoke(this.getOutputs(), this.getProject().files());
            } catch (Exception e) {
                throw new RuntimeException("Exception calling setPreviousOutputFiles ", e);
            }
            try {
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
                CleaningJavaCompiler compiler = new CleaningJavaCompiler(javaCompiler, getOutputs());
                final WorkResult execute = compiler.execute(spec);
                setDidWork(execute.getDidWork());
            } catch (Exception e) {
                throw new RuntimeException("Exception compiling ", e);
            }
        }

    }

}

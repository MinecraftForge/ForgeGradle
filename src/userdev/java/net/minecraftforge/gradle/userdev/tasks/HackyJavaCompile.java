/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.internal.compile.Compiler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/*
 *  A terrible hack to use JavaCompile while bypassing
 *  Gradle's normal task infrastructure.
 *  This is internal API Modders DO NOT reference this.
 *  It can and will be removed if we get a better way to do this.
 */
@CacheableTask
public class HackyJavaCompile extends JavaCompile {

    public void doHackyCompile() {

        // What follows is a horrible hack to allow us to call JavaCompile
        // from our dependency resolver.
        // As described in https://github.com/MinecraftForge/ForgeGradle/issues/550,
        // invoking Gradle tasks in the normal way can lead to deadlocks
        // when done from a dependency resolver.

        setCompiler();

        this.getOutputs().setPreviousOutputFiles(this.getProject().files());
        final DefaultJavaCompileSpec spec = reflectCreateSpec();
        spec.setSourceFiles(getSource());
        Compiler<JavaCompileSpec> compiler = createCompiler(spec);
        final WorkResult execute = compiler.execute(spec);
        setDidWork(execute.getDidWork());
    }

    private void setCompiler() {
        JavaToolchainService service = getProject().getExtensions().getByType(JavaToolchainService.class);
        Provider<JavaCompiler> compiler = service.compilerFor(s -> s.getLanguageVersion().set(JavaLanguageVersion.of(this.getSourceCompatibility())));
        this.getJavaCompiler().set(compiler);
    }

    private DefaultJavaCompileSpec reflectCreateSpec() {
        try {
            Method createSpec = JavaCompile.class.getDeclaredMethod("createSpec");
            createSpec.setAccessible(true);
            return (DefaultJavaCompileSpec) createSpec.invoke(this);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find JavaCompile#createSpec method; might be on incompatible newer version of Gradle", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking JavaCompile#createSpec", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Compiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec) {
        try {
            Method createCompiler = JavaCompile.class.getDeclaredMethod("createCompiler");
            createCompiler.setAccessible(true);
            return (Compiler<JavaCompileSpec>) createCompiler.invoke(this);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find JavaCompile#createCompiler method; might be on incompatible newer version of Gradle", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking JavaCompile#createCompiler", e);
        }
    }

}

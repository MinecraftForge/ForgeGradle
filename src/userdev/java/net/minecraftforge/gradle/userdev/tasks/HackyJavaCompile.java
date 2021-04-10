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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.internal.tasks.compile.JavaCompileSpec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.language.base.internal.compile.Compiler;

/*
 *  A terrible hack to use JavaCompile while bypassing
 *  Gradle's normal task infrastructure.
 *  This is internal API Modderrs DO NOT referencee this.
 *  It can and will be removed if we get a better way to do this.
 */
public class HackyJavaCompile extends JavaCompile {

    public void doHackyCompile() {

        // What follows is a horrible hack to allow us to call JavaCompile
        // from our dependency resolver.
        // As described in https://github.com/MinecraftForge/ForgeGradle/issues/550,
        // invoking Gradle tasks in the normal way can lead to deadlocks
        // when done from a dependency resolver.

        this.getOutputs().setPreviousOutputFiles(this.getProject().files());

        final JavaCompileSpec spec = createSpec();
        spec.setSourceFiles(getSource());
        Compiler<JavaCompileSpec> compiler = createCompiler(spec);
        final WorkResult execute = compiler.execute(spec);
        setDidWork(execute.getDidWork());
    }

    /*
     * We need to get a Compiler<JavaCompileSpec> for our compile hack.
     * However, Gradle 6.8.x and Gradle 7.0 create the toolchain compiler differently.
     * To avoid having to maintain separate code for each major version, we'll call their private methods using reflection.
     * Since the methods are different across the two major versions, we have to do some checks for the two methods.
     *
     * Our targets:
     * - Gradle 6.8.1: https://github.com/gradle/gradle/blob/d5661e3f0e07a8caff705f1badf79fb5df8022c4/subprojects/language-java/src/main/java/org/gradle/api/tasks/compile/JavaCompile.java#L242
     *      method signature: CleaningJavaCompiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec)
     * - Gradle 7.0: https://github.com/gradle/gradle/blob/31f14a87d93945024ab7a78de84102a3400fa5b2/subprojects/language-java/src/main/java/org/gradle/api/tasks/compile/JavaCompile.java#L302
     *      method signature: CleaningJavaCompiler<JavaCompileSpec> createCompiler()
     */
    @SuppressWarnings("unchecked")
    private Compiler<JavaCompileSpec> createCompiler(JavaCompileSpec spec) {
        try {
            //noinspection RedundantSuppression
            try { // Gradle 6.8.1

                //noinspection JavaReflectionMemberAccess
                Method createCompiler = JavaCompile.class.getDeclaredMethod("createCompiler", JavaCompileSpec.class);
                createCompiler.setAccessible(true);
                return (Compiler<JavaCompileSpec>) createCompiler.invoke(this, spec);

            } catch (NoSuchMethodException gradle6) { // Method is missing, might be Gradle 7.0
                //noinspection RedundantSuppression
                try {
                    //noinspection JavaReflectionMemberAccess
                    Method createCompiler = JavaCompile.class.getDeclaredMethod("createCompiler");
                    createCompiler.setAccessible(true);
                    return (Compiler<JavaCompileSpec>) createCompiler.invoke(this);

                } catch (NoSuchMethodException gradle7) { // Both are missing, we're incompatible with this Gradle version
                    RuntimeException ex = new RuntimeException("Could not find JavaCompile#createCompiler method; might be on incompatible newer version of Gradle", gradle7);
                    ex.addSuppressed(gradle6); // So we have both exceptions in the stack trace
                    throw ex;
                }
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking JavaCompile#createCompiler", e);
        }
    }

    private DefaultJavaCompileSpec createSpec() {
        try {
            Method createSpec = JavaCompile.class.getDeclaredMethod("createSpec");
            createSpec.setAccessible(true);
            return (DefaultJavaCompileSpec) createSpec.invoke(this);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Could not find JavaCompile#createSpec method; might be on incompatible newer version of Gradle");
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Exception while invoking JavaCompile#createSpec", e);
        }
    }
}

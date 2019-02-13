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

import com.google.common.collect.Sets;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.util.Set;

import javax.annotation.Nullable;

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
        this.getOutputs().setHistory(new TaskExecutionHistory() {

            @Override
            public Set<File> getOutputFiles() {
                // We explicitly clear the output directory
                // ourselves, so it's okay that this is totally wrong.
                return Sets.newHashSet();
            }

            @Nullable
            @Override
            public OverlappingOutputs getOverlappingOutputs() {
                return null;
            }

            @Nullable
            @Override
            public OriginTaskExecutionMetadata getOriginExecutionMetadata() {
                return null;
            }
        });

        // Do the actual compilation,
        // bypassing a bunch of Gradle's other stuff (e.g. internal event listener mechanism)
        this.compile();
    }

}

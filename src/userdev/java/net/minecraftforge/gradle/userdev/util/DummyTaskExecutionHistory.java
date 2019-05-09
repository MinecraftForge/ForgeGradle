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

import com.google.common.collect.Sets;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;

import java.io.File;
import java.util.Set;

import javax.annotation.Nullable;

public class DummyTaskExecutionHistory implements TaskExecutionHistory {


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

}

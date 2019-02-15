/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.tasks.fernflower;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;

public class FernFlowerInvoker {
    
    public static void main(String[] args) throws Exception {
        // Data file is the first argument
        File data = new File(args[0]);
        if (!data.exists()) {
            throw new IllegalStateException("missing data file");
        }
        FernFlowerSettings settings = readSettings(data);
        runFernFlower(settings);
    }
    
    @SuppressWarnings("serial")
    private static FernFlowerSettings readSettings(File data) throws IOException
    {
        return ResourceGroovyMethods.withObjectInputStream(data, new Closure<FernFlowerSettings>(FernFlowerInvoker.class, FernFlowerInvoker.class) {
            @Override
            public FernFlowerSettings call(Object... args)
            {
                ObjectInputStream in = (ObjectInputStream) args[0];
                try {
                    return (FernFlowerSettings) in.readObject();
                } catch (Exception e) {
                    // never returns, only throws
                    return (FernFlowerSettings) throwRuntimeException(e);
                }
            }
        });
    }

    public static void runFernFlower(FernFlowerSettings settings) throws IOException {
        PrintStreamLogger logger = new PrintStreamLogger(new PrintStream(settings.getTaskLogFile()));
        BaseDecompiler decompiler = new BaseDecompiler(new ByteCodeProvider(), new ArtifactSaver(settings.getCacheDirectory()), settings.getMapOptions(), logger);

        decompiler.addSpace(settings.getJarFrom(), true);
        for (File library : settings.getClasspath()) {
            decompiler.addSpace(library, false);
        }

        decompiler.decompileContext();
    }

}

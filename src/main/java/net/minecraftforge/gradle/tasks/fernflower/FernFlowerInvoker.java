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

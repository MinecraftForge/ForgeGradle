package net.minecraftforge.gradle.tasks.fernflower;

import org.jetbrains.java.decompiler.main.extern.IVariableNameProvider;
import org.jetbrains.java.decompiler.main.extern.IVariableNamingFactory;
import org.jetbrains.java.decompiler.struct.StructMethod;

// must be public for FF
public class AdvancedJadRenamerFactory implements IVariableNamingFactory {
    @Override
    public IVariableNameProvider createFactory(StructMethod arg0)
    {
        return new AdvancedJadRenamer(arg0);
    }
}

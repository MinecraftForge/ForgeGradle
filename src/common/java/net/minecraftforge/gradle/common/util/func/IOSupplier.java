package net.minecraftforge.gradle.common.util.func;

import java.io.IOException;

public interface IOSupplier<T> {

    T get() throws IOException;

}
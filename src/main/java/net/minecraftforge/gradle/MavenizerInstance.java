package net.minecraftforge.gradle;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;

import java.io.File;

public interface MavenizerInstance {
    Provider<ExternalModuleDependency> getDependency();
    Provider<String> getMappingVersion();
    Provider<String> getToSrg();
    Provider<File> getToSrgFile();
    Provider<String> getToObf();
    Provider<File> getToObfFile();
}

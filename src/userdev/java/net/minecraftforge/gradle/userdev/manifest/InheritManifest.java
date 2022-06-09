package net.minecraftforge.gradle.userdev.manifest;

import groovy.lang.Closure;
import org.gradle.api.java.archives.Manifest;

public interface InheritManifest extends Manifest
{
    InheritManifest inheritFrom(Object... inheritPaths);

    InheritManifest inheritFrom(Object inheritPaths, Closure closure);
}

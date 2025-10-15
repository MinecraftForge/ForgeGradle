# ForgeGradle

[![Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/net.minecraftforge.gradle)](https://plugins.gradle.org/plugin/net.minecraftforge.gradle)

Welcome to ForgeGradle, Minecraft Forge's Gradle plugin. It is a small, simple,
and effective plugin with the primary purpose of bootstrapping Minecraft Forge's
toolchain to aid in the development of mods targeting Minecraft Forge.

## Basic Usage

### Applying the Plugin

ForgeGradle 7 can be applied to projects or settings by using the following:

```groovy
plugins {
    id 'net.minecraftforge.gradle' version '<version>'
}
```

ForgeGradle 7 and later can now be found on the
[Gradle Plugin Portal][Gradle Plugin Portal]. Adding the Forge maven to the
plugin management repositories is no longer necessary.

### Depending on Minecraft

```groovy
dependencies {
    implementation minecraft.dependency('net.minecraftforge:forge:1.21.10-60.0.0')
}
```

## API Design

ForgeGradle 7, the latest iteration of ForgeGradle, has a completely different
approach to API design. If you plan on interfacing with the plugin in
unconventional ways, there are a few things to keep in mind.

- ForgeGradle is **stateless by default.**
  - This means that if nothing is asked of it, it will not do anything.
  - The only exception to this rule is ForgeGradle Magic, which is covered in
    its own documentation entry.
- ForgeGradle's **implementation and internal code is inaccessible.**
  - All implementations are package-private, so they cannot be accessed by other
    plugins or extended from. Everything that is public API has been
    deliberately made so.
- ForgeGradle is **not the toolchain.**
  - Unlike ForgeGradle 6 and its predecessors, ForgeGradle 7 does not include
    the toolchain. It instead acts as the configuring interface and invocation
    of the toolchain. If you are interested in contributing to the toolchain,
    please see the [Minecraft Mavenizer][Mavenizer].

[Gradle Plugin Portal]: https://plugins.gradle.org
[Mavenizer]: https://github.com/MinecraftForge/MinecraftMavenizer
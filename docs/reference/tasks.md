#Tasks
The tasks added in ForgeGradle and what they do. This is not even close to all the tasks that ForgeGradle actually creates, but they are the ones relevant to most users.

### SetupCIWorkspace
This task is designed for headless Continuous Integration servers that have neither the necessity nor the capability to run the Minecraft client. It does the bare minimum required to compile a minecraft mod; specifically the following:
 - Download MC jars
 - Download forge/fml userdev package
 - Merge MC server and Client jars
 - Apply forge/fml binary patches
 - download MCP mappings
 - Generate deobfuscation and reobfuscation SRG files from mappings
 - Deobfuscate patched jar to MCP names and apply access transformers  (creates the forgeBin artifact)

### SetupDevWorkspace
This task sets up a development environment for minecraft mod development. This includes everything required to compile minecraft mods as well as run Minecraft with all of its sounds and assets. It does everything that SetupCIWorkspace does plus the following additional tasks:
 - Downloads and extracts LWJGL natives
 - Download minecraft AssetIndex file
 - Downloads Minecraft assets as defined in the AssetIndex
 - Generates the GradleStart and GradleStartServer main classes

### SetupDecompWorkspace
This task is by far the most popular setup task. It sets up a full development environment similar to setupDevWorkspace with the key difference that it decompiles Minecraft and provides the decompiled java code in a browseable form. It specifically does the following:
- Download MC jars
- Download forge/fml userdev package
- Merge MC server and Client jars
- download MCP mappings
- Generate deobfuscation and reobfuscation SRG files from mappings
- Deobfuscate merged jar to SRG names and apply access transformers
- Decompile the deobfuscated Minecraft jar and apply source transformations
- Apply forge/fml patches (creates the forgeSrc-sources artifact)
- Extract the patches minecraft source jar
- Recompile the Minecraft patches sources
- Repackage the compiled Minecraft classes (creates the forgeSrc artifact)
- Downloads and extracts LWJGL natives
- Download minecraft AssetIndex file
- Downloads Minecraft assets as defined in the AssetIndex
- Generates the GradleStart and GradleStartServer main classes

### runClient and runServer
These tasks are for convenience and start the Minecraft client and server respectively. They depend on the jar task, so the mod is fully compiled and packaged, but not reobfuscated before this task actually executes. Both of these tasks are of the type [JavaExec](https://gradle.org/docs/current/dsl/org.gradle.api.tasks.JavaExec.html) And are configurable just like others of their type ith the `runClient { .. }` and `runServer { .. }` blocks respectively.

### build
This task builds the mod and creates a reobfuscated distributable Jar file. If this task fails with the error along the lines of `'forgeBin' was not found`, then you must run one of the 3 setup tasks above first. Depending on the buildscript, this task attempts to automagically setup itself, but this does not always succeed. The root of this issue is that the setup tasks create the Minecraft jar that is compiled against, and some build.gradles attempt to reference that jar before it is created. **This task is added by the default gradle `java` plugin, and not actually by ForgeGradle.**

### cleanCache
Due to the nature of Minecraft mod projects, it is extremely common that someone may have several separate ForgeGradle projects that all use the exact same Minecraft/Forge/FML version. Because of this, ForgeGradle caches many things in USER_HOME/.gradle/caches/minecraft  so that they can be used my multiple projects from this central location. This task exists to clean this cache entirely and delete all of its contents. After this task is run, all projects must run their setup tasks again to re-create the Minecraft dependencies.

### reobf
This task reobfuscates the compiled and packaged mod so that it can be installed into an obfuscated Production Minecraft environment. BY default, this task is depended upon by the `build` task, and depends on the Jar task. This is one of the few tasks added by ForgeGradle that is meant to be configured by end users. Further information for this task is documented here. **TODO: link seperate page**

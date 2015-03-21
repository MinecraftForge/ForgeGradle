#Tasks
The tasks added in ForgeGradle and what they do. This is not even close to all the tasks that ForgeGradle actually creates, but they are the ones relevant to most users.

## Setups

#### SetupCIWorkspace
This task is designed for headless Continuous Integration servers that have neither the necessity nor the capability to run the Minecraft client. It does the bare minimum required to compile a minecraft mod; specifically the following:
 - Download MC jars
 - Download forge/fml userdev package
 - Merge MC server and Client jars
 - Apply forge/fml binary patches
 - download MCP mappings
 - Generate deobfuscation and reobfuscation SRG files from mappings
 - Deobfuscate patched jar to MCP names and apply access transformers  (creates the forgeBin artifact)


#### SetupDevWorkspace
This task sets up a development environment for minecraft mod development. This includes everything required to compile minecraft mods as well as run Minecraft with all of its sounds and assets. It does everything that SetupCIWorkspace does plus the following additional tasks:
 - Downloads and extracts LWJGL natives
 - Download minecraft AssetIndex file
 - Downloads Minecraft assets as defined in the AssetIndex
 - Generates the GradleStart and GradleStartServer main classes


#### SetupDecompWorkspace
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

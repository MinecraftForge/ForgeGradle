# Minecraft{} extension object

There are some public methods in the minecraft extension object that are meant to be used internally by ForgeGradle, and will likely not be used by most users. These methods will not be documented here. You are free to look at the code.

- **Dont forget**
    - `object.someMethod(someArg)` === `object.someMethod someArg`
    - `object.someProp = "thing"` === `object.setSomeProp("thing")`
    - `object.someProp = "thing"` === `object.setSomeProp("thing")`

### Versions
- `String getApiVersion()`
    - Specifically returns the MinecraftForge or FML version in form `McVersion-#.#.#.#`. the branch is appended if it exists resulting in the following: `McVersion-#.#.#.#-branch`
- `void setVersion(String version)`
    - The argument passed to this method is parsed and verified against the [forge](http://files.minecraftforge.net/maven/net/minecraftforge/forge/json) or [fml](http://files.minecraftforge.net/maven/net/minecraftforge/fml/json) jsons.
    - Possible argument notations:
        - A forge/fml buildnumber, eg: `1232`
        - A forge/fml promotion,, eg: `1.7.10-latest`, `1.7.10-recommended`
        - A forge/fml version string potentially including a branch appendage, eg : `10.12.0.1048`, `11.14.0.1288-1.8`
        - A full minecraft + forge/fml version string potentially including a branch appendage in form `mcVersion-forge/fmlversion(-branch)`: `1.7.2-10.12.0.1048`, `1.8-11.14.0.1288-1.8`
- `String getVersion()`
    - Returns the only the minecraft version. eg: `1.7.10`, `1.8`. For the Forge/fml version, use getApiVersion()
- `String getMappings()`, `void setMappings(String mappings)`
    - The argument for this method has only 1 notation, and that is `channel_version`, and it is validated against the [mcp json](http://export.mcpbot.bspk.rs/versions.json). The valid channels are currently `stable`, `stable_nodoc`, `snapshot`, `snapshot_nodoc` where the nodoc variants do not include the javadoc comments. If the argument is in the form `something_custom` the validation is skipped, and the user is expected to have made arrangements for their custom MCP mapping version. More information on custom MCP snapshots can be found [here](https://gist.github.com/AbrarSyed/0d1f7ebea8767e264038).


### Source Replacement
- `void replace(Object token, Object replacement)`
    - Adds a token replacement entry. Takes Objects to allow for Closures and other objects that will be resolved late.
- `void replace(Map<Object, Object> map)`
    - same as above, just allows for the following notation. `minecraft { replace "key": "val", "key2", "val2" }`
- `void replaceIn(String path)`
    - Adds a file inclusion. If nothing is included with this method, then all files will be preocessed for replacement. Otherwise only included files will be processed. Files will be checked against the include paths using file.endsWith(path) until one matches. Thus one may specify just a filename, or an entire path.

### Miscellanious
- `void srgExtra(String srgLine)`
    - Used to include arbitrary SRG lines into the reobfuscation SRG file. An example of this used is in shading dependencies, though it can be used for other things as well.
- `String getRunDir()` `void getRunDir()`
    - Allows the setting and getting of the configured run directory. This configures where the working directory will be when the runClient and runServer tasks are run. All minecraft files will be generated and saved in this folder, be sure to gitignore it.
- Access Transformers, all these do is add files as Access Transformers
    - `void at(Object obj)`
    - `void at(Object... objs)`
    - `void accessT(Object obj)`
    - `void acessTs(Object... objs)`
    - `void accessTransformers(Object obj)`
    - `void accessTransformers(Object... objs)`
- `int getMaxFuzz()` `void setMaxFuzz(int fuzz)`
    - A rarely used method to set the amount of fuzzing to do when applying the forge/fml patches. By default the fuzz is 0 and the patch application will fail at even the slightest miss. Setting the fuzz to a number greater than 1 can fix issues that arrise from imperfect patches or interfering access transformers.

#CurseForge Publishing

##About
ForgeGradle has the ability to publish your mod and its related files to [CurseForge](http://minecraft.curseforge.com/) automatically on a build.

##Required Information
The following information is necessary to setup CurseForge publishing:

1. **Your CurseForge project's ID.** This is the numerical part of the URL to your project. For example: For [Railcraft](http://minecraft.curseforge.com/mc-mods/51195-railcraft) (`http://minecraft.curseforge.com/mc-mods/51195-railcraft`) the ID would be `51195`.
2. **Your CurseForge API key.** This is a key that is tied to your account, rather than the project. You can generate an API key by going to <http://minecraft.curseforge.com/account> and clicking on `API Tokens`.
3. **Any related projects' slugs.** A project's slug can be found as the portion to the right of the project ID in the URL. For the above example of [Railcraft](http://minecraft.curseforge.com/mc-mods/51195-railcraft), the slug would be `railcraft`.

##Setting up your build.gradle
* Apply the curseforge plugin to your build.gradle by adding `apply plugin: 'curseforge'`
The best place for this is right underneath the `apply plugin: 'forge'` line:
```java
apply plugin: 'forge'
apply plugin: 'curseforge'
```

* Add the following block somewhere underneath.
```groovy
curse {
    projectId = '<Your CurseForge Project ID>'
    apiKey = project.curseForgeApiKey // Notice we don't put the actual key here
    releaseType = '<alpha|beta|release>'
}
```
The above properties are required, the following ones are optional.
```groovy
curse {
    displayName = "MyFancyMod version $project.version"
    artifact = file('some/other/file.jar')
    changelog = 'I made some changes!'
    addGameVersion '1.7.10', '1.7.2'

    additionalArtifact devJar
    additionalArtifact "$project.buildDir/libs/A-Custom-File.txt"

    relatedProject 'forgeservertools'
    relatedProject 'forgeservertools': 'embeddedLibrary'
}
```

#### What they do
Let's talk about what each of these does.

* `projectId` This is your CurseForge ID that we got from the URL above. <br>
* `apiKey` This is your key that authorizes you to publish files to your projects. __*We do not put the actual API Key here because it is private and should not be shared with anyone*__. If this build.gradle is published online somewhere, we need to make sure people can't see your API Key. Check the [next section](#setting-up-a-gradleproperties) to see how to accomplish this.
* `releaseType` This must be set to one of the following: `alpha`, `beta`, or `release`. This is used to tell end users how stable this file may be. Only files marked as `relase` will be downloaded by the Curse Client.
* `displayName` This allows you to change the display name of the main artifact. The display name will be shown to end users instead of the file name.
* `artifact` This allows you to change what is published to curseforge. By default the main obfuscated jar is used. This can be set to another task that produces an archive, a direct file reference, or a relative file reference.
* `changelog` This is used to tell your users what has changed since the last version. Check  [HERE](https://gist.github.com/matthewprenger/e13d1a4e47ccb5c920a9) for a useful changelog script on Jenkins servers.
* `addGameVersion` This allows you to add extra compatible Minecraft versions. The one specified in your `minecraft {}` block is used by default.
* `additionalArtifact` This allows you to publish additional files to CurseForge in the same build. Examples are: deobfuscated jars, source jars, changelog files, etc. A single artifact can be specified, or a comma separated list of artifacts
* `relatedProject` This allows you to specify another project on CurseForge that is 'related' to your file. You can specify just the project's slug (`relatedProject 'forgeservertools'`), and `requiredLibrary` will be used by default. This means the related project is __*required*__ for you file to work. Alternatively, you can use the following format to specify the relation type: `relatedProject 'forgeservertools': 'embeddedLibrary'`. This notation means that the related project is __*embedded*__ into this file.
    * All valid relation types are:
        * `requiredLibrary` The project is __*required*__ for your file to operate correctly.
        * `embeddedLibrary` The project is __*embedded*__ into your file and doesn't need to be downloaded separately.
        * `optionalLibrary` The project will add functionality or features to your file, but isn't required.
        * `tool` The project is a tool that can be used with your file.
        * `incompatible` The project is __not__ compatible with your file.

##Setting up a gradle.properties
In order to keep some things private, like your `apiKey`, we can create a `gradle.properties` file in a couple of places:

1. Creating a `gradle.properties` in the same directory as your `build.gradle` will cause all properties defined in it to apply __Only__ to that project.

2. Creating a `gradle.properties` in the `.gradle` folder of your user home directory will cause all properties defined in it to apply to __All__ projects executed under your user account. On Linux/Mac this file is `~/.gradle/gradle.properties`. On Windows this file is `C:\Users\<USERNAME>\.gradle\gradle.properties`.

It should be noted that the `gradle.properties` in your user home takes precedence over the one in the project directory.


Once you get that file setup, we need to add one line to it.
```ini
curseForgeApiKey=xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

Replace the X's with your actual CurseForge API key. <br>
Also make sure that if you created this file in your project directory, that you add it to your `.gitignore` file to make sure it stay's out of the Git repo.

Any properties defined in these files can be referenced in your build.gradle as a property of the `project` object, such as `project.curseForgeApiKey`.

If you have machines or developers that don't have your CurseForge API key, you can check for the existence of the property and provide a default that won't work, but also won't break:

```groovy
curse {
    projectId = '12345'
    apiKey = project.hasProperty('curseForgeApiKey') ? project.curseForgeApiKey : ''
    releaseType = 'alpha'
}
```

##Publishing
To actually publish your build to CurseForge, all you need to do is run the gradle `curse` task:

`gradlew(.bat) build curse`

##Aditional Links
[Jenkins Changelog Script](https://gist.github.com/matthewprenger/e13d1a4e47ccb5c920a9)

#Shading

## What?
To **Shade** a library is to take the contents files of said library, put them in your own jar, *and* change their package.This is different from ***packaging*** which is simply shipping the libraries files in side your own jar without relocating them to a different package. The term ***fatjar*** is commonly used to refer to jars that have the application as well as its dependencies packaged or shaded into them.

## Why?
When a mod uses a 3rd party library like EJML or something else, it is usually necessary for this library to be present at runtime to avoid ClassDefNotFoundErrors. Minecraft and Forge have some libraries that they use and are guaranteed to be present at runtime such as GSON and Google Guava, but what about extra libraries that are not already included? **Shading** is one way to ensure that these extra libraries are present at runtime.

*Packaging fixes this problem as well. Put the libraries in your own jar so they exist at runtime, and everything is perfect. Why relocate? The answer to this question lies in the fact that other people make different mods. Perhaps you packaged AbrarLib 1.0 that has only 1 class named abrar.lib.AbrarClass with your mod. Lets say someone else made a different mod that packaged AbrarLib 2.0 that has 2 classes, abrar.lib.AbrarClass and abrar.lib.AbrarHelper. When your mod tries to reference AbrarClass? What about when the other mod tries to use AbrarClass? It is not guaranteed that both will work correctly, both could break in unpredictable ways. Relocating fixes this problem. The AbrarLib file shaded into your mod would have the name my.mod.abrarlib.AbrarClass, and the AbrarLib files in the other mod would have the names other.mod.abrarlib.AbrarClass and other.mod.abrarlib.AbrarHelper. Because the classes have different names, there is no longer any confusion between the two. This is why we shade instead of package runtime dependencies.

## How?

```
configurations {
    shade
    compile.extendsFrom shade
}
```
This defines a new configuration (dependency container) that is seperate from the *compile* configuration. However the **compile** configuration extends rom our new configuration, so everythign we define in the **shade** configuration will still find its way into the **compile** configuration. This allows us to seperate our to-be-shaded depednencies from the rest of the compile dependencies.

```
dependencies {
    shade 'com.googlecode.efficient-java-matrix-library:ejml:0.25'
}
```
Notice how this dependency decleration line starts with **shade** instead of **compile**. This means we are adding that specific dependency to the **shade** configuration we defined above instead of the **compile** configuration. This dependency{} block ***must*** be after the configuration block{}. Gradle scripts are sequential, and we cannot use something that is yet to be defined.

```
jar {
    configurations.shade.each { dep ->
        from(project.zipTree(dep)){
            exclude 'META-INF', 'META-INF/**'
        }
    }
}
```
This part is what tells gradle to actually include the contents of the dependencies in the **shade** configuration inside your jar file. When this is done, the libraries are not relocated just yet, so any duplicattions would probably be problematic. This section may be anywhere in the build.gradle.

```
minecraft {
    srgExtra "PK: org/ejml your/new/package/here/ejml"
    srgExtra "PK: org/ejml/alg your/new/package/here/ejml/alg"
}
```
This is the section that tells Gradle what package you want to relocate where. This takes advantage of ForgeGradle's reobfuscation mechanism, and thus these lines only take effect at the reobfuscation step of the build. These **srgExtra** strings are indeed SRG lines, and can be specified for individual classes, fields, or methods as well as packages. This section can be located anywhere in the build.gradle.

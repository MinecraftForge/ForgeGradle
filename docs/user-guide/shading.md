**Shading** is relocating and packaging. When you shade a java library, you copy it into your own jar, and then relocate it to another package. This is useful because other projects may include the a different version of the same library you used. If neither project relocated the 3rd library, then when used together the projects could fail with ClassDefNotFOundError, MethodNotFoundError, or other similair errors. While you can use other methods to shade libraries into your jar like the gradle shadow plugin, the way explained here is exclusive to ForgeGradle and uses no other plugins or dependencies. It should be obvious that this is only necessary when you require a library at runtime, and this section assumes that you already know to define the dependency without shading.

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
    srgExtra "PK:org/ejml your/new/package/here/ejml"
    srgExtra "PK:org/ejml/alg your/new/package/here/ejml/alg"
}
```
This is the section that tells Gradle what package you want to relocate where. This takes advantage of ForgeGradle's reobfuscation mechanism, and thus these lines only take effect at the reobfuscation step of the build. These **srgExtra** strings are indeed SRG lines, and can be specified for individual classes, fields, or methods as well as packages. This section can be located anywhere in the build.gradle.


#The CookBook
This page houses a collection of recipes. In this sense a Recipe is specific exact instructions to accomplish a given task. The majority of these recipes are simply snippets of Gradle scripts with minimal explanation needed for an absolute beginner to use.

###Creating a deobfuscated jar
```groovy
task deobfJar(type: Jar) {
    from sourceSets.main.output
    classifier "dev"
}
artifacts {
    archives deobfJar
}
tasks.assemble.dependsOn deobfJar
```

###Creating a source jar
```groovy
task sourcesJar(type: Jar) {
    from sourceSets.main.allSource
    classifier "sources"
}
artifacts {
    archives sourcesJar
}
tasks.assemble.dependsOn sourcesJar
```

###Creating a javadoc jar
```groovy
task javadocJar(type: Jar, dependsOn: javadoc) {
    from javadoc.destinationDir
    classifier "javadoc"
}
artifacts {
    archives javadocJar
}
tasks.assemble.dependsOn javadocJar
```

###Using environment variables
In this case, `BUILD_NUMBER` is the environment variable we care about.
```groovy
if (System.getenv().BUILD_NUMBER != null) {
    version = "1.2.3.${System.getenv().BUILD_NUMBER}"
}
```

### Getting a git hash

```groovy
    buildscript {
        repositories {
            mavenCentral()
        }
        dependencies {
            classpath 'org.ajoberstar:gradle-git:0.10.1'
        }
    }

    import org.ajoberstar.grgit.Grgit

    if (file('.git').exists()) {
        Grgit repo = Grgit.open('.')
        def gitHash = repo.log().find().abbreviatedId
        version += ".$gitHash"
    }
```

### Shading
In this example, the EJML library is being shaded. This can be done with almost any jar you can define as a dependency. Each library has a different package its files are in, in this case EJMLs root package is ```org.ejml```, and I want to relocate it to ```your.new.package.here.ejml```. Notice that the periods have been replaced with slashes.
```
minecraft {
    srgExtra "PK:org/ejml your/new/package/here/ejml"
}

configurations {
    shade
    compile.extendsFrom shade
}

dependencies {
    shade 'com.googlecode.efficient-java-matrix-library:ejml:0.25'
}

jar {
    configurations.shade.each { dep ->
        from(project.zipTree(dep)){
            exclude 'META-INF', 'META-INF/**'
            // you may exclude other things here if you want, or maybe copy the META-INF
        }
    }
}
```
